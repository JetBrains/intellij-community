// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.workspace.VirtualFileUrls;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.storage.EntityPointer;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.platform.workspace.storage.url.VirtualFileUrl;
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager;
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.IndexableSetContributorFilesIterator;
import com.intellij.util.indexing.roots.ModuleFilesIteratorImpl;
import com.intellij.util.indexing.roots.origin.IndexingUrlRootHolder;
import com.intellij.util.indexing.roots.origin.IndexingUrlSourceRootHolder;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileSetRecognizer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

@ApiStatus.Internal
@ApiStatus.Experimental
public final class ReincludedRootsUtil {
  private static final Logger LOG = Logger.getInstance(ReincludedRootsUtil.class);

  private ReincludedRootsUtil() {
  }

  public interface Classifier {
    @NotNull
    Collection<VirtualFile> getFilesFromAdditionalLibraryRootsProviders();

    /**
     * All SDKs and Libraries included
     */
    @NotNull
    Collection<IndexableFilesIterator> createIteratorsFromWorkspaceFiles(@NotNull Project project);

    Collection<IndexableFilesIterator> createIteratorsFromFilesFromIndexableSetContributors(@NotNull Project project);
  }

  public static @NotNull Classifier classifyFiles(@NotNull Project project,
                                                  @NotNull Collection<VirtualFile> files) {
    return new CustomizableRootsBuilder(project, files);
  }

  private static final class CustomizableRootsBuilder implements Classifier {
    private final List<ModuleRootData> filesFromModulesContent = new ArrayList<>();
    private final List<ContentRootData<?>> filesFromContent = new ArrayList<>();
    private final List<ExternalRootData<?>> filesFromExternal = new ArrayList<>();
    private final List<CustomKindRootData<?>> filesFromCustomKind = new ArrayList<>();
    private final List<VirtualFile> filesFromIndexableSetContributors = new ArrayList<>();
    private final List<VirtualFile> filesFromAdditionalLibraryRootsProviders = new ArrayList<>();

    private CustomizableRootsBuilder(@NotNull Project project, @NotNull Collection<VirtualFile> files) {
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
          return workspaceFileIndex.findFileSet(file, true, true, true, true, true, true, true);
        }).expireWith(project).executeSynchronously();

        if (fileSet == null) {
          filesFromIndexableSetContributors.add(file);
          continue;
        }
        if (!fileSet.getKind().isIndexable()) {
          continue;
        }

        EntityPointer<?> entityPointer = WorkspaceFileSetRecognizer.INSTANCE.getEntityPointer(fileSet);

        if (fileSet.getKind() == WorkspaceFileKind.CONTENT || fileSet.getKind() == WorkspaceFileKind.TEST_CONTENT) {
          LOG.assertTrue(entityPointer != null, "Content element's fileSet without entity reference, " + fileSet);
          Module module = WorkspaceFileSetRecognizer.INSTANCE.getModuleForContent(fileSet);
          VirtualFileUrl url = VirtualFileUrls.toVirtualFileUrl(file, fileUrlManager);
          if (module != null) {
            addModuleRoot(module, url);
          }
          else {
            addContentRoot(entityPointer, url);
          }
          continue;
        }


        if (WorkspaceFileSetRecognizer.INSTANCE.isFromAdditionalLibraryRootsProvider(fileSet)) {
          filesFromAdditionalLibraryRootsProviders.add(file);
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

    private void addModuleRoot(Module module, VirtualFileUrl url) {
      filesFromModulesContent.add(new ModuleRootData(module, url));
    }

    private void addContentRoot(EntityPointer<?> entityPointer, VirtualFileUrl url) {
      filesFromContent.add(new ContentRootData<>(entityPointer, url));
    }

    private void addExternalRoots(EntityPointer<?> entityPointer, List<VirtualFileUrl> roots, List<VirtualFileUrl> sourceRoots) {
      filesFromExternal.add(new ExternalRootData<>(entityPointer, roots, sourceRoots));
    }

    private void addCustomKindRoot(EntityPointer<?> entityPointer, VirtualFileUrl file) {
      filesFromCustomKind.add(new CustomKindRootData<>(entityPointer, file));
    }

    private record ModuleRootData(@NotNull Module module,
                                  @NotNull VirtualFileUrl url) {
    }

    private record ContentRootData<E extends WorkspaceEntity>(@NotNull EntityPointer<E> entityPointer, @NotNull VirtualFileUrl url) {
    }

    private record ExternalRootData<E extends WorkspaceEntity>(@NotNull EntityPointer<E> entityPointer,
                                                               @NotNull List<VirtualFileUrl> roots,
                                                               @NotNull List<VirtualFileUrl> sourceRoots) {
    }

    private record CustomKindRootData<E extends WorkspaceEntity>(@NotNull EntityPointer<E> entityPointer,
                                                                 @NotNull VirtualFileUrl fileUrl) {
    }

    @Override
    public @NotNull Collection<IndexableFilesIterator> createIteratorsFromWorkspaceFiles(@NotNull Project project) {

      List<IndexableFilesIterator> result = new ArrayList<>();

      for (ModuleRootData data : filesFromModulesContent) {
        VirtualFile root = VirtualFileUrls.getVirtualFile(data.url());
        if (root != null) {
          result.add(new ModuleFilesIteratorImpl(data.module(), root, true, true));
        }
      }

      Map<EntityPointer<?>, List<VirtualFileUrl>> contentRootUrls = new HashMap<>();
      for (ContentRootData<?> data : filesFromContent) {
        getUrlList(contentRootUrls, data.entityPointer()).add(data.url());
      }
      for (Map.Entry<EntityPointer<?>, List<VirtualFileUrl>> entry : contentRootUrls.entrySet()) {
        result.addAll(IndexableEntityProviderMethods.INSTANCE.createGenericContentEntityIterators(
          entry.getKey(), IndexingUrlRootHolder.Companion.fromUrls(entry.getValue())));
      }

      Map<EntityPointer<?>, ExternalRootUrls> externalRootUrls = new HashMap<>();
      for (ExternalRootData<?> data : filesFromExternal) {
        getExternalRootUrls(externalRootUrls, data.entityPointer()).add(data);
      }
      for (Map.Entry<EntityPointer<?>, ExternalRootUrls> entry : externalRootUrls.entrySet()) {
        result.addAll(IndexableEntityProviderMethods.INSTANCE.createExternalEntityIterators(entry.getKey(), entry.getValue().toHolder()));
      }

      Map<EntityPointer<?>, List<VirtualFileUrl>> customKindRootUrls = new HashMap<>();
      for (CustomKindRootData<?> data : filesFromCustomKind) {
        getUrlList(customKindRootUrls, data.entityPointer()).add(data.fileUrl());
      }
      for (Map.Entry<EntityPointer<?>, List<VirtualFileUrl>> entry : customKindRootUrls.entrySet()) {
        result.addAll(IndexableEntityProviderMethods.INSTANCE.createCustomKindEntityIterators(
          entry.getKey(), IndexingUrlRootHolder.Companion.fromUrls(entry.getValue())));
      }
      return result;
    }

    private static <K> @NotNull List<VirtualFileUrl> getUrlList(@NotNull Map<K, List<VirtualFileUrl>> map, @NotNull K key) {
      List<VirtualFileUrl> roots = map.get(key);
      if (roots == null) {
        roots = new ArrayList<>();
        map.put(key, roots);
      }
      return roots;
    }

    private static @NotNull ExternalRootUrls getExternalRootUrls(@NotNull Map<EntityPointer<?>, ExternalRootUrls> map,
                                                                 @NotNull EntityPointer<?> key) {
      ExternalRootUrls roots = map.get(key);
      if (roots == null) {
        roots = new ExternalRootUrls();
        map.put(key, roots);
      }
      return roots;
    }

    private static final class ExternalRootUrls {
      private final List<VirtualFileUrl> roots = new ArrayList<>();
      private final List<VirtualFileUrl> sourceRoots = new ArrayList<>();

      private void add(@NotNull ExternalRootData<?> data) {
        roots.addAll(data.roots());
        sourceRoots.addAll(data.sourceRoots());
      }

      private @NotNull IndexingUrlSourceRootHolder toHolder() {
        return IndexingUrlSourceRootHolder.Companion.fromUrls(roots, sourceRoots);
      }
    }

    @Override
    public Collection<IndexableFilesIterator> createIteratorsFromFilesFromIndexableSetContributors(@NotNull Project project) {
      if (filesFromIndexableSetContributors.isEmpty()) {
        return Collections.emptyList();
      }
      List<IndexableFilesIterator> result = new ArrayList<>();
      for (IndexableSetContributor contributor : IndexableSetContributor.EP_NAME.getExtensionList()) {
        Set<VirtualFile> applicationRoots =
          collectAndRemoveFilesUnder(filesFromIndexableSetContributors, contributor.getAdditionalRootsToIndex());
        Set<VirtualFile> projectRoots =
          collectAndRemoveFilesUnder(filesFromIndexableSetContributors,
                                     contributor.getAdditionalProjectRootsToIndex(project));

        if (!applicationRoots.isEmpty()) {
          result.add(new IndexableSetContributorFilesIterator(contributor, applicationRoots, false));
        }
        if (!projectRoots.isEmpty()) {
          result.add(new IndexableSetContributorFilesIterator(contributor, projectRoots, true));
        }
        if (filesFromIndexableSetContributors.isEmpty()) {
          break;
        }
      }
      return result;
    }
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
