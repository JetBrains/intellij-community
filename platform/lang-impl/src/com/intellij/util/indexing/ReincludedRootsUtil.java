// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.navigation.ItemPresentation;
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.roots.IndexingContributorCustomization;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.util.indexing.roots.builders.IndexableSetContributorFilesIteratorBuilder;
import com.intellij.util.indexing.roots.builders.SyntheticLibraryIteratorBuilder;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileSetRecognizer;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge;
import com.intellij.workspaceModel.storage.EntityReference;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryId;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

import static com.intellij.util.indexing.roots.IndexableEntityProvider.IndexableIteratorBuilder;

public final class ReincludedRootsUtil {
  private static final Logger LOG = Logger.getInstance(ReincludedRootsUtil.class);

  private ReincludedRootsUtil() {
  }

  public record Data(@NotNull List<IndexableIteratorBuilder> builders,
                     @NotNull List<VirtualFile> rootsFromAdditionalLibraryRootsProviders) {
  }


  @NotNull
  public static List<IndexableIteratorBuilder> createBuildersForReincludedFiles(@NotNull Project project,
                                                                                @NotNull Collection<VirtualFile> reincludedRoots) {
    Data data = createBuildersDataForReincludedFiles(project, reincludedRoots);
    if (data.rootsFromAdditionalLibraryRootsProviders().isEmpty()) return data.builders;

    List<IndexableIteratorBuilder> result = new ArrayList<>(data.builders);
    List<VirtualFile> rootsFromLibs = data.rootsFromAdditionalLibraryRootsProviders;
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

  @NotNull
  public static Data createBuildersDataForReincludedFiles(@NotNull Project project,
                                                          @NotNull Collection<VirtualFile> reincludedRoots) {
    if (reincludedRoots.isEmpty()) return new Data(Collections.emptyList(), Collections.emptyList());

    List<VirtualFile> filesFromIndexableSetContributors = new ArrayList<>();
    List<VirtualFile> filesFromAdditionalLibraryRootsProviders = new ArrayList<>();
    CustomizableRootsBuilder customizableRootsBuilder = new CustomizableRootsBuilder();

    EntityStorage entityStorage = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
    WorkspaceFileIndex workspaceFileIndex = WorkspaceFileIndex.getInstance(project);
    ArrayList<IndexableIteratorBuilder> result = new ArrayList<>();
    Iterator<VirtualFile> iterator = reincludedRoots.iterator();
    while (iterator.hasNext()) {
      VirtualFile file = iterator.next();
      WorkspaceFileSet fileSet = workspaceFileIndex.findFileSet(file, true, true, true, true);
      if (fileSet == null) {
        filesFromIndexableSetContributors.add(file);
        iterator.remove();
        continue;
      }

      EntityReference<?> entityReference = WorkspaceFileSetRecognizer.INSTANCE.getEntityReference(fileSet);

      if (fileSet.getKind() == WorkspaceFileKind.CONTENT || fileSet.getKind() == WorkspaceFileKind.TEST_CONTENT) {
        LOG.assertTrue(entityReference != null, "Content element's fileSet without entity reference, " + fileSet);
        Module module = WorkspaceFileSetRecognizer.INSTANCE.getModuleForContent(fileSet);
        if (module != null) {
          customizableRootsBuilder.addModuleRoot(module, entityReference, file);
        }
        else {
          customizableRootsBuilder.addContentRoot(entityReference, file);
        }
        iterator.remove();
        continue;
      }

      //here we have WorkspaceFileKind.EXTERNAL or WorkspaceFileKind.EXTERNAL_SOURCE
      Collection<VirtualFile> roots =
        fileSet.getKind() == WorkspaceFileKind.EXTERNAL ? Collections.singletonList(file) : Collections.emptyList();
      Collection<VirtualFile> sourceRoots =
        fileSet.getKind() == WorkspaceFileKind.EXTERNAL_SOURCE ? Collections.singletonList(file) : Collections.emptyList();

      Sdk sdk = WorkspaceFileSetRecognizer.INSTANCE.getSdk(fileSet);
      if (sdk != null) {
        result.addAll(IndexableIteratorBuilders.INSTANCE.forSdk(sdk, Collections.singletonList(file)));
        iterator.remove();
        continue;
      }

      if (WorkspaceFileSetRecognizer.INSTANCE.isFromAdditionalLibraryRootsProvider(fileSet)) {
        filesFromAdditionalLibraryRootsProviders.add(file);
        iterator.remove();
        continue;
      }

      LibraryId libraryId = WorkspaceFileSetRecognizer.INSTANCE.getLibraryId(fileSet, entityStorage);
      if (libraryId != null) {
        result.addAll(IndexableIteratorBuilders.INSTANCE.forLibraryEntity(libraryId, true, roots, sourceRoots));
        iterator.remove();
        continue;
      }

      LOG.assertTrue(entityReference != null, "External element's fileSet without entity reference, " + fileSet);
      customizableRootsBuilder.addExternalRoots(entityReference, roots, sourceRoots);
      iterator.remove();
    }

    result.addAll(customizableRootsBuilder.createBuilders(entityStorage));

    if (!filesFromIndexableSetContributors.isEmpty()) {
      for (IndexableSetContributor contributor : IndexableSetContributor.EP_NAME.getExtensionList()) {
        Set<VirtualFile> applicationRoots =
          collectAndRemoveFilesUnder(filesFromIndexableSetContributors, contributor.getAdditionalRootsToIndex());
        Set<VirtualFile> projectRoots =
          collectAndRemoveFilesUnder(filesFromIndexableSetContributors, contributor.getAdditionalProjectRootsToIndex(project));

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
    }

    if (!reincludedRoots.isEmpty()) {
      throw new IllegalStateException("Roots were not found: " + StringUtil.join(reincludedRoots, "\n"));
    }
    return new Data(result, filesFromAdditionalLibraryRootsProviders);
  }

  private static class CustomizableRootsBuilder {
    private final Set<EntityReference<?>> references = new HashSet<>();
    private final List<ModuleRootData<?>> filesFromModulesContent = new ArrayList<>();
    private final List<ContentRootData<?>> filesFromContent = new ArrayList<>();
    private final List<ExternalRootData<?>> filesFromExternal = new ArrayList<>();

    public void addModuleRoot(Module module, EntityReference<?> entityReference, VirtualFile file) {
      filesFromModulesContent.add(new ModuleRootData<>(entityReference, ((ModuleBridge)module).getModuleEntityId(), file));
      references.add(entityReference);
    }

    public void addContentRoot(EntityReference<?> entityReference, VirtualFile file) {
      filesFromContent.add(new ContentRootData<>(entityReference, file));
      references.add(entityReference);
    }

    public void addExternalRoots(EntityReference<?> entityReference, Collection<VirtualFile> roots, Collection<VirtualFile> sourceRoots) {
      filesFromExternal.add(new ExternalRootData<>(entityReference, roots, sourceRoots));
      references.add(entityReference);
    }

    record ModuleRootData<E extends WorkspaceEntity>(@NotNull EntityReference<E> entityReference,
                                                     @NotNull ModuleId moduleId,
                                                     @NotNull VirtualFile file) {
      public @NotNull Collection<IndexableIteratorBuilder> createBuilders(Map<EntityReference<?>, WorkspaceEntity> referenceMap,
                                                                          Map<Class<WorkspaceEntity>, CustomizingIndexingContributor<?, ?>> contributorMap) {
        IndexingContributorCustomization<E, ?> customization = findCustomization(entityReference, referenceMap, contributorMap);
        if (customization == null) {
          return IndexableIteratorBuilders.INSTANCE.forModuleRootsFileBased(moduleId, Collections.singletonList(file));
        }
        return IndexableIteratorBuilders.INSTANCE.forModuleAwareCustomizedContentEntity(moduleId, entityReference,
                                                                                        Collections.singletonList(file), customization);
      }
    }

    record ContentRootData<E extends WorkspaceEntity>(@NotNull EntityReference<E> entityReference, @NotNull VirtualFile file) {
      public @NotNull Collection<IndexableIteratorBuilder> createBuilders(Map<EntityReference<?>, WorkspaceEntity> referenceMap,
                                                                          Map<Class<WorkspaceEntity>, CustomizingIndexingContributor<?, ?>> contributorMap) {
        IndexingContributorCustomization<E, ?> customization = findCustomization(entityReference, referenceMap, contributorMap);
        return IndexableIteratorBuilders.INSTANCE.forGenericContentEntity(entityReference, Collections.singletonList(file), customization);
      }
    }

    record ExternalRootData<E extends WorkspaceEntity>(@NotNull EntityReference<E> entityReference, @NotNull Collection<VirtualFile> roots,
                                                       @NotNull Collection<VirtualFile> sourceRoots) {
      public @NotNull Collection<IndexableIteratorBuilder> createBuilders(Map<EntityReference<?>, WorkspaceEntity> referenceMap,
                                                                          Map<Class<WorkspaceEntity>, CustomizingIndexingContributor<?, ?>> contributorMap) {
        IndexingContributorCustomization<E, ?> customization = findCustomization(entityReference, referenceMap, contributorMap);
        return IndexableIteratorBuilders.INSTANCE.forExternalEntity(entityReference, roots, sourceRoots, customization);
      }
    }

    @NotNull
    Collection<IndexableIteratorBuilder> createBuilders(@NotNull EntityStorage entityStorage) {
      if (references.isEmpty()) return Collections.emptyList();

      Map<EntityReference<?>, WorkspaceEntity> referenceMap =
        ContainerUtil.map2MapNotNull(references, ref -> Pair.create(ref, ref.resolve(entityStorage)));

      Map<Class<WorkspaceEntity>, CustomizingIndexingContributor<?, ?>> customizingContributorsMap =
        ContainerUtil.map2MapNotNull(WorkspaceFileIndexImpl.Companion.getEP_NAME().getExtensionList(),
                                     contributor -> {
                                       if (contributor instanceof CustomizingIndexingContributor) {
                                         return Pair.create((Class<WorkspaceEntity>)contributor.getEntityClass(),
                                                            (CustomizingIndexingContributor<?, ?>)contributor);
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
      for (ExternalRootData<?> data : filesFromExternal) {
        result.addAll(data.createBuilders(referenceMap, customizingContributorsMap));
      }
      return result;
    }
  }

  @Nullable
  private static <E extends WorkspaceEntity> IndexingContributorCustomization<E, ?> findCustomization(@NotNull EntityReference<E> reference,
                                                                                                      @NotNull Map<EntityReference<?>, WorkspaceEntity> referenceMap,
                                                                                                      @NotNull Map<Class<WorkspaceEntity>, CustomizingIndexingContributor<?, ?>> contributorMap) {
    E entity = (E)referenceMap.get(reference);
    if (entity == null) {
      return null;
    }
    CustomizingIndexingContributor<E, ?> contributor =
      (CustomizingIndexingContributor<E, ?>)contributorMap.get(entity.getEntityInterface());
    return contributor == null ? null : IndexingContributorCustomization.Companion.create(contributor, entity);
  }

  @NotNull
  private static Set<VirtualFile> collectAndRemoveFilesUnder(List<VirtualFile> fileToCheck, Set<VirtualFile> roots) {
    return collectAndRemove(fileToCheck, file -> VfsUtilCore.isUnder(file, roots));
  }

  @NotNull
  private static Set<VirtualFile> collectAndRemove(@NotNull List<VirtualFile> fileToCheck,
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
