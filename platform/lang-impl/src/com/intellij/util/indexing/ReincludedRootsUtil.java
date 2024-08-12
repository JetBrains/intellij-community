// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.workspace.VirtualFileUrls;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.jps.entities.LibraryId;
import com.intellij.platform.workspace.jps.entities.ModuleId;
import com.intellij.platform.workspace.storage.EntityPointer;
import com.intellij.platform.workspace.storage.EntityStorage;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.platform.workspace.storage.url.VirtualFileUrl;
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.roots.IndexableIteratorPresentation;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.util.indexing.roots.builders.IndexableSetContributorFilesIteratorBuilder;
import com.intellij.util.indexing.roots.builders.SyntheticLibraryIteratorBuilder;
import com.intellij.util.indexing.roots.origin.IndexingUrlRootHolder;
import com.intellij.util.indexing.roots.origin.IndexingUrlSourceRootHolder;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileSetRecognizer;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

import static com.intellij.util.indexing.roots.IndexableEntityProvider.IndexableIteratorBuilder;

@ApiStatus.Experimental
public final class ReincludedRootsUtil {
  private static final Logger LOG = Logger.getInstance(ReincludedRootsUtil.class);

  private ReincludedRootsUtil() {
  }

  public static @NotNull Collection<IndexableIteratorBuilder> createBuildersForReincludedFiles(@NotNull Project project,
                                                                                               @NotNull Collection<VirtualFile> reincludedRoots) {
    if (reincludedRoots.isEmpty()) return Collections.emptyList();
    return classifyFiles(project, reincludedRoots).createAllBuilders(project);
  }

  public interface Classifier {
    @NotNull
    Collection<VirtualFile> getFilesFromAdditionalLibraryRootsProviders();

    /**
     * All SDKs and Libraries included
     */
    @NotNull
    Collection<IndexableIteratorBuilder> createBuildersFromWorkspaceFiles();

    Collection<IndexableIteratorBuilder> createBuildersFromFilesFromIndexableSetContributors(@NotNull Project project);

    @NotNull
    Collection<IndexableIteratorBuilder> createAllBuilders(@NotNull Project project);
  }

  public static @NotNull Classifier classifyFiles(@NotNull Project project,
                                                  @NotNull Collection<VirtualFile> files) {
    return new CustomizableRootsBuilder(project, files);
  }

  private static final class CustomizableRootsBuilder implements Classifier {
    private final @NotNull EntityStorage entityStorage;
    private final Set<EntityPointer<?>> pointers = new HashSet<>();
    private final List<ModuleRootData<?>> filesFromModulesContent = new ArrayList<>();
    private final List<ContentRootData<?>> filesFromContent = new ArrayList<>();
    private final List<ExternalRootData<?>> filesFromExternal = new ArrayList<>();
    private final List<CustomKindRootData<?>> filesFromCustomKind = new ArrayList<>();
    private final MultiMap<Sdk, VirtualFile> filesFromSdks = MultiMap.createSet();
    private final MultiMap<LibraryId, VirtualFile> sourceFilesFromLibraries = MultiMap.createSet();
    private final MultiMap<LibraryId, VirtualFile> classFilesFromLibraries = MultiMap.createSet();
    private final List<VirtualFile> filesFromIndexableSetContributors = new ArrayList<>();
    private final List<VirtualFile> filesFromAdditionalLibraryRootsProviders = new ArrayList<>();

    private CustomizableRootsBuilder(@NotNull Project project, @NotNull Collection<VirtualFile> files) {
      entityStorage = WorkspaceModel.getInstance(project).getCurrentSnapshot();
      classifyFiles(project, files);
    }

    @Override
    public @NotNull Collection<VirtualFile> getFilesFromAdditionalLibraryRootsProviders() {
      return filesFromAdditionalLibraryRootsProviders;
    }

    void classifyFiles(@NotNull Project project, @NotNull Collection<VirtualFile> files) {
      WorkspaceFileIndex workspaceFileIndex = WorkspaceFileIndex.getInstance(project);
      VirtualFileUrlManager fileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager();
      for (VirtualFile file : files) {
        WorkspaceFileSet fileSet = ReadAction.nonBlocking(() -> {
          return workspaceFileIndex.findFileSet(file, true, true, true, true, true);
        }).expireWith(project).executeSynchronously();

        if (fileSet == null) {
          filesFromIndexableSetContributors.add(file);
          continue;
        }

        EntityPointer<?> entityPointer = WorkspaceFileSetRecognizer.INSTANCE.getEntityPointer(fileSet);

        if (fileSet.getKind() == WorkspaceFileKind.CONTENT || fileSet.getKind() == WorkspaceFileKind.TEST_CONTENT) {
          LOG.assertTrue(entityPointer != null, "Content element's fileSet without entity reference, " + fileSet);
          Module module = WorkspaceFileSetRecognizer.INSTANCE.getModuleForContent(fileSet);
          VirtualFileUrl url = VirtualFileUrls.toVirtualFileUrl(file, fileUrlManager);
          if (module != null) {
            addModuleRoot(module, entityPointer, url);
          }
          else {
            addContentRoot(entityPointer, url);
          }
          continue;
        }

        //here we have WorkspaceFileKind.EXTERNAL or WorkspaceFileKind.EXTERNAL_SOURCE
        Sdk sdk = WorkspaceFileSetRecognizer.INSTANCE.getSdk(fileSet);
        if (sdk != null) {
          addSdkFile(sdk, file);
          continue;
        }

        if (WorkspaceFileSetRecognizer.INSTANCE.isFromAdditionalLibraryRootsProvider(fileSet)) {
          filesFromAdditionalLibraryRootsProviders.add(file);
          continue;
        }

        LibraryId libraryId = WorkspaceFileSetRecognizer.INSTANCE.getLibraryId(fileSet, entityStorage);
        if (libraryId != null) {
          addLibraryFile(libraryId, file, fileSet.getKind() == WorkspaceFileKind.EXTERNAL_SOURCE);
          continue;
        }

        LOG.assertTrue(entityPointer != null, "External element's fileSet without entity reference, " + fileSet);
        VirtualFileUrl url = VirtualFileUrls.toVirtualFileUrl(file, fileUrlManager);
        if (fileSet.getKind() == WorkspaceFileKind.EXTERNAL_SOURCE) {
          addExternalRoots(entityPointer, Collections.emptyList(), Collections.singletonList(url));
        }
        else if (fileSet.getKind() == WorkspaceFileKind.EXTERNAL) {
          addExternalRoots(entityPointer, Collections.singletonList(url), Collections.emptyList());
        }
        else {
          addCustomKindRoot(entityPointer, url);
        }
      }
    }

    private void addModuleRoot(Module module, EntityPointer<?> entityPointer, VirtualFileUrl url) {
      filesFromModulesContent.add(new ModuleRootData<>(entityPointer, ((ModuleBridge)module).getModuleEntityId(), url));
      pointers.add(entityPointer);
    }

    private void addContentRoot(EntityPointer<?> entityPointer, VirtualFileUrl url) {
      filesFromContent.add(new ContentRootData<>(entityPointer, url));
      pointers.add(entityPointer);
    }

    private void addExternalRoots(EntityPointer<?> entityPointer, List<VirtualFileUrl> roots, List<VirtualFileUrl> sourceRoots) {
      filesFromExternal.add(new ExternalRootData<>(entityPointer, roots, sourceRoots));
      pointers.add(entityPointer);
    }

    private void addCustomKindRoot(EntityPointer<?> entityPointer, VirtualFileUrl file) {
      filesFromCustomKind.add(new CustomKindRootData<>(entityPointer, file));
      pointers.add(entityPointer);
    }

    private void addSdkFile(Sdk sdk, VirtualFile file) {
      filesFromSdks.putValue(sdk, file);
    }

    private void addLibraryFile(LibraryId id, VirtualFile file, boolean isSource) {
      if (isSource) {
        sourceFilesFromLibraries.putValue(id, file);
      }
      else {
        classFilesFromLibraries.putValue(id, file);
      }
    }

    private record ModuleRootData<E extends WorkspaceEntity>(@NotNull EntityPointer<E> entityPointer,
                                                             @NotNull ModuleId moduleId,
                                                             @NotNull VirtualFileUrl url) {
      private @NotNull Collection<IndexableIteratorBuilder> createBuilders(Map<EntityPointer<?>, WorkspaceEntity> referenceMap,
                                                                           Map<Class<WorkspaceEntity>, CustomizingIndexingPresentationContributor<?>> contributorMap) {
        IndexableIteratorPresentation presentation = findPresentation(entityPointer, referenceMap, contributorMap);
        if (presentation == null) {
          return IndexableIteratorBuilders.INSTANCE.forModuleRootsFileBased(moduleId, IndexingUrlRootHolder.Companion.fromUrl(url));
        }
        return IndexableIteratorBuilders.INSTANCE.forModuleAwareCustomizedContentEntity(moduleId,
                                                                                        entityPointer,
                                                                                        IndexingUrlRootHolder.Companion.fromUrl(url),
                                                                                        presentation);
      }
    }

    private record ContentRootData<E extends WorkspaceEntity>(@NotNull EntityPointer<E> entityPointer, @NotNull VirtualFileUrl url) {
      public @NotNull Collection<IndexableIteratorBuilder> createBuilders(Map<EntityPointer<?>, WorkspaceEntity> referenceMap,
                                                                          Map<Class<WorkspaceEntity>, CustomizingIndexingPresentationContributor<?>> contributorMap) {
        IndexableIteratorPresentation customization = findPresentation(entityPointer, referenceMap, contributorMap);
        return IndexableIteratorBuilders.INSTANCE.forGenericContentEntity(entityPointer, IndexingUrlRootHolder.Companion.fromUrl(url),
                                                                          customization);
      }
    }

    private record ExternalRootData<E extends WorkspaceEntity>(@NotNull EntityPointer<E> entityPointer,
                                                               @NotNull List<VirtualFileUrl> roots,
                                                               @NotNull List<VirtualFileUrl> sourceRoots) {
      public @NotNull Collection<IndexableIteratorBuilder> createBuilders(Map<EntityPointer<?>, WorkspaceEntity> referenceMap,
                                                                          Map<Class<WorkspaceEntity>, CustomizingIndexingPresentationContributor<?>> contributorMap) {
        IndexableIteratorPresentation presentation = findPresentation(entityPointer, referenceMap, contributorMap);
        return IndexableIteratorBuilders.INSTANCE.forExternalEntity(entityPointer,
                                                                    IndexingUrlSourceRootHolder.Companion.fromUrls(roots, sourceRoots),
                                                                    presentation);
      }
    }

    private record CustomKindRootData<E extends WorkspaceEntity>(@NotNull EntityPointer<E> entityPointer,
                                                                 @NotNull VirtualFileUrl fileUrl) {
      public @NotNull Collection<IndexableIteratorBuilder> createBuilders(Map<EntityPointer<?>, WorkspaceEntity> referenceMap,
                                                                          Map<Class<WorkspaceEntity>, CustomizingIndexingPresentationContributor<?>> contributorMap) {
        IndexableIteratorPresentation customization = findPresentation(entityPointer, referenceMap, contributorMap);
        return IndexableIteratorBuilders.INSTANCE.forCustomKindEntity(entityPointer, IndexingUrlRootHolder.Companion.fromUrl(fileUrl),
                                                                      customization);
      }
    }

    @Override
    public @NotNull Collection<IndexableIteratorBuilder> createBuildersFromWorkspaceFiles() {
      Map<EntityPointer<?>, WorkspaceEntity> referenceMap =
        ContainerUtil.map2MapNotNull(pointers, ref -> Pair.create(ref, ref.resolve(entityStorage)));

      Map<Class<WorkspaceEntity>, CustomizingIndexingPresentationContributor<?>> customizingContributorsMap =
        ContainerUtil.map2MapNotNull(WorkspaceFileIndexImpl.Companion.getEP_NAME().getExtensionList(),
                                     contributor -> {
                                       if (contributor instanceof CustomizingIndexingPresentationContributor<?>) {
                                         return Pair.create( (Class<WorkspaceEntity>)contributor.getEntityClass(),
                                                            (CustomizingIndexingPresentationContributor<?>)contributor);
                                       }
                                       return null;
                                     });

      List<IndexableIteratorBuilder> result = new ArrayList<>();
      for (ModuleRootData<?> data : filesFromModulesContent) {
        result.addAll(data.createBuilders(referenceMap, customizingContributorsMap));
      }
      for (ContentRootData<?> data : filesFromContent) {
        result.addAll(data.createBuilders(referenceMap, customizingContributorsMap));
      }
      for (Map.Entry<LibraryId, Collection<VirtualFile>> entry : sourceFilesFromLibraries.entrySet()) {
        result.addAll(IndexableIteratorBuilders.INSTANCE.
                        forLibraryEntity(entry.getKey(), true, Collections.emptyList(), entry.getValue()));
      }
      for (Map.Entry<LibraryId, Collection<VirtualFile>> entry : classFilesFromLibraries.entrySet()) {
        result.addAll(IndexableIteratorBuilders.INSTANCE.
                        forLibraryEntity(entry.getKey(), true, entry.getValue(), Collections.emptyList()));
      }
      for (Map.Entry<Sdk, Collection<VirtualFile>> entry : filesFromSdks.entrySet()) {
        result.addAll(IndexableIteratorBuilders.INSTANCE.forSdk(entry.getKey(), entry.getValue()));
      }
      for (ExternalRootData<?> data : filesFromExternal) {
        result.addAll(data.createBuilders(referenceMap, customizingContributorsMap));
      }
      for (CustomKindRootData<?> data : filesFromCustomKind) {
        result.addAll(data.createBuilders(referenceMap, customizingContributorsMap));
      }
      return result;
    }

    @Override
    public Collection<IndexableIteratorBuilder> createBuildersFromFilesFromIndexableSetContributors(@NotNull Project project) {
      if (filesFromIndexableSetContributors.isEmpty()) {
        return Collections.emptyList();
      }
      List<IndexableIteratorBuilder> result = new ArrayList<>();
      for (IndexableSetContributor contributor : IndexableSetContributor.EP_NAME.getExtensionList()) {
        Set<VirtualFile> applicationRoots =
          collectAndRemoveFilesUnder(filesFromIndexableSetContributors, contributor.getAdditionalRootsToIndex());
        Set<VirtualFile> projectRoots =
          collectAndRemoveFilesUnder(filesFromIndexableSetContributors,
                                     contributor.getAdditionalProjectRootsToIndex(project));

        if (!applicationRoots.isEmpty()) {
          result.add(
            new IndexableSetContributorFilesIteratorBuilder(null, contributor.getDebugName(), applicationRoots, false, contributor));
        }
        if (!projectRoots.isEmpty()) {
          result.add(new IndexableSetContributorFilesIteratorBuilder(null, contributor.getDebugName(), projectRoots, true, contributor));
        }
        if (filesFromIndexableSetContributors.isEmpty()) {
          break;
        }
      }
      return result;
    }

    private @NotNull Collection<IndexableIteratorBuilder> createBuildersFromFilesFromAdditionalLibraryRootsProviders(@NotNull Project project) {
      if (filesFromAdditionalLibraryRootsProviders.isEmpty()) return Collections.emptyList();
      List<IndexableIteratorBuilder> result = new ArrayList<>();
      List<VirtualFile> rootsFromLibs = new ArrayList<>(filesFromAdditionalLibraryRootsProviders);
      for (AdditionalLibraryRootsProvider provider : AdditionalLibraryRootsProvider.EP_NAME.getExtensionList()) {
        for (SyntheticLibrary library : provider.getAdditionalProjectLibraries(project)) {
          Set<VirtualFile> roots = collectAndRemove(rootsFromLibs, file -> library.contains(file, true, true));
          if (!roots.isEmpty()) {
            String name = library instanceof ItemPresentation ? ((ItemPresentation)library).getPresentableText() : null;
            result.add(new SyntheticLibraryIteratorBuilder(library, name, roots));
          }
          if (rootsFromLibs.isEmpty()) {
            break;
          }
        }
        if (rootsFromLibs.isEmpty()) {
          break;
        }
      }

      if (!rootsFromLibs.isEmpty()) {
        LOG.error("Failed fo find any SyntheticLibrary roots for " + StringUtil.join(rootsFromLibs, "\n"));
      }
      return result;
    }

    @Override
    public @NotNull Collection<IndexableIteratorBuilder> createAllBuilders(@NotNull Project project) {
      List<IndexableIteratorBuilder> result = new ArrayList<>(createBuildersFromWorkspaceFiles());
      result.addAll(createBuildersFromFilesFromIndexableSetContributors(project));
      result.addAll(createBuildersFromFilesFromAdditionalLibraryRootsProviders(project));
      return result;
    }
  }

  private static @Nullable <E extends WorkspaceEntity> IndexableIteratorPresentation findPresentation(@NotNull EntityPointer<E> reference,
                                                                                                      @NotNull Map<EntityPointer<?>, WorkspaceEntity> referenceMap,
                                                                                                      @NotNull Map<Class<WorkspaceEntity>, CustomizingIndexingPresentationContributor<?>> contributorMap) {
    E entity = (E)referenceMap.get(reference);
    if (entity == null) {
      return null;
    }
    CustomizingIndexingPresentationContributor<E> contributor =
      (CustomizingIndexingPresentationContributor<E>)contributorMap.get(entity.getEntityInterface());
    return contributor == null ? null : contributor.customizeIteratorPresentation(entity);
  }

  private static @NotNull Set<VirtualFile> collectAndRemoveFilesUnder(Collection<VirtualFile> fileToCheck, Set<VirtualFile> roots) {
    return collectAndRemove(fileToCheck, file -> VfsUtilCore.isUnder(file, roots));
  }

  private static @NotNull Set<VirtualFile> collectAndRemove(@NotNull Collection<VirtualFile> fileToCheck,
                                                            @NotNull Predicate<VirtualFile> predicateToRemove) {
    Iterator<VirtualFile> iterator = fileToCheck.iterator();
    Set<VirtualFile> roots = new HashSet<>();
    while (iterator.hasNext()) {
      VirtualFile next = iterator.next();
      if (predicateToRemove.test(next)) {
        roots.add(next);
        iterator.remove();
      }
    }
    return roots;
  }
}
