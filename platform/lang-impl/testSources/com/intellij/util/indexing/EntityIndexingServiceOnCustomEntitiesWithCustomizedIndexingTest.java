// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.customizingIteration.ExternalEntityIndexableIterator;
import com.intellij.util.indexing.customizingIteration.GenericContentEntityIterator;
import com.intellij.util.indexing.customizingIteration.ModuleAwareContentEntityIterator;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.origin.ModuleAwareContentEntityOrigin;
import com.intellij.util.indexing.testEntities.IndexingTestEntity;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar;
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleContentOrSourceRootData;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl;
import com.intellij.workspaceModel.storage.EntityReference;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.url.VirtualFileUrl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public class EntityIndexingServiceOnCustomEntitiesWithCustomizedIndexingTest extends EntityIndexingServiceTestBase {
  private static class MyModuleContentIterator implements ModuleAwareContentEntityIterator {
    private final Module module;
    private final EntityReference<? extends WorkspaceEntity> reference;
    private final Collection<VirtualFile> roots;

    private MyModuleContentIterator(Module module,
                                    EntityReference<? extends WorkspaceEntity> reference,
                                    Collection<? extends VirtualFile> roots) {
      this.module = module;
      this.reference = reference;
      this.roots = new ArrayList<>(roots);
    }

    @Override
    public boolean iterateFiles(@NotNull Project project, @NotNull ContentIterator fileIterator, @NotNull VirtualFileFilter fileFilter) {
      return true;
    }

    @Override
    public @NotNull Set<String> getRootUrls(@NotNull Project project) {
      return ContainerUtil.map2Set(roots, root -> root.getPath());
    }

    @Override
    public @NotNull ModuleAwareContentEntityOrigin getOrigin() {
      return new MyContentOrigin(module, reference, roots);
    }
  }

  private static class MyContentOrigin implements ModuleAwareContentEntityOrigin {
    private final Module myModule;
    private final EntityReference<?> myReference;
    private final Collection<VirtualFile> myRoots;

    MyContentOrigin(@NotNull Module module,
                    @NotNull EntityReference<?> reference,
                    @NotNull Collection<VirtualFile> roots) {

      myModule = module;
      myReference = reference;
      myRoots = roots;
    }

    @NotNull
    @Override
    public Module getModule() {
      return myModule;
    }

    @NotNull
    @Override
    public EntityReference<?> getReference() {
      return myReference;
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getRoots() {
      return myRoots;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MyContentOrigin origin = (MyContentOrigin)o;
      return Objects.equals(myModule, origin.myModule) &&
             Objects.equals(myReference, origin.myReference) &&
             Objects.equals(myRoots, origin.myRoots);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myModule, myReference, myRoots);
    }
  }

  private class ModuleContentContributor implements CustomizingIndexingContributor<IndexingTestEntity, Void> {

    @Override
    public @Nullable Void getCustomizationData(@NotNull IndexingTestEntity entity) {
      return null;
    }

    @Override
    public @NotNull Collection<? extends ModuleAwareContentEntityIterator> createModuleAwareContentIterators(@NotNull Module module,
                                                                                                             @NotNull EntityReference<IndexingTestEntity> reference,
                                                                                                             @NotNull Collection<? extends VirtualFile> roots,
                                                                                                             @Nullable Void customization) {
      return Collections.singletonList(new MyModuleContentIterator(module, reference, roots));
    }

    @Override
    public @NotNull Collection<? extends GenericContentEntityIterator> createGenericContentIterators(@NotNull EntityReference<IndexingTestEntity> reference,
                                                                                                     @NotNull Collection<? extends VirtualFile> roots,
                                                                                                     @Nullable Void customization) {
      throw new IllegalStateException("createGenericContentIterators shouldn't be called");
    }

    @Override
    public Collection<? extends ExternalEntityIndexableIterator> createExternalEntityIterators(@NotNull EntityReference<IndexingTestEntity> reference,
                                                                                               @NotNull Collection<? extends VirtualFile> roots,
                                                                                               @NotNull Collection<? extends VirtualFile> sourceRoots,
                                                                                               @Nullable Void customization) {
      throw new IllegalStateException("createExternalEntityIterators shouldn't be called");
    }

    @NotNull
    @Override
    public Class<IndexingTestEntity> getEntityClass() {
      return IndexingTestEntity.class;
    }

    @Override
    public void registerFileSets(@NotNull IndexingTestEntity entity,
                                 @NotNull WorkspaceFileSetRegistrar registrar,
                                 @NotNull EntityStorage storage) {
      ModuleContentOrSourceRootData data = new ModuleContentOrSourceRootData() {

        @Nullable
        @Override
        public VirtualFile getCustomContentRoot() {
          return null;
        }

        @NotNull
        @Override
        public Module getModule() {
          return myModule;
        }
      };
      for (VirtualFileUrl root : entity.getRoots()) {
        registrar.registerFileSet(root, WorkspaceFileKind.CONTENT, entity, data);
      }
    }
  }

  public void testAddingContentModuleCustomWorkspaceEntity() throws Exception {
    WorkspaceFileIndexContributor<IndexingTestEntity> contributor = new ModuleContentContributor();
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), WorkspaceFileIndexImpl.Companion.getEP_NAME(), contributor,
                                           getTestRootDisposable());
    File root = createTempDir("customRoot");
    VirtualFile virtualRoot = Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root.toPath()));
    List<VirtualFileUrl> urls = getUrls(virtualRoot);

    doTest(() -> EntityIndexingServiceOnCustomEntitiesTest.createAndRegisterEntity(urls, Collections.emptyList(), myProject),
           (entity) -> {
             return Collections.singletonList(new MyModuleContentIterator(myModule, entity.createReference(),
                                                                          Collections.singletonList(virtualRoot)));
           });
  }

  protected <T> void doTest(ThrowableComputable<? extends T, ? extends Exception> generator,
                            Function<T, ? extends Collection<IndexableFilesIterator>> expectedIteratorsProducer) throws Exception {
    super.doTest(generator, (t) -> EntityIndexingServiceOnCustomEntitiesTest.removeAllIndexingTestEntities(myProject),
                 expectedIteratorsProducer);
  }
}
