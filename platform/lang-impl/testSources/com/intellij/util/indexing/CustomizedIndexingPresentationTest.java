// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.workspace.storage.EntityStorage;
import com.intellij.platform.workspace.storage.url.VirtualFileUrl;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.IndexableIteratorPresentation;
import com.intellij.util.indexing.roots.ModuleAwareContentEntityIteratorImpl;
import com.intellij.util.indexing.roots.origin.IndexingRootHolder;
import com.intellij.util.indexing.testEntities.IndexingTestEntity;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar;
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleRelatedRootData;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class CustomizedIndexingPresentationTest extends EntityIndexingServiceTestBase {

  public static final IndexableIteratorPresentation TEST_PRESENTATION =
    IndexableIteratorPresentation.create("test debug name", "test indexing progress text", "test scanning progress text");

  private class ModuleContentContributor implements CustomizingIndexingPresentationContributor<IndexingTestEntity> {

    @Override
    public @Nullable IndexableIteratorPresentation customizeIteratorPresentation(@NotNull IndexingTestEntity entity) {
      return TEST_PRESENTATION;
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
      ModuleRelatedRootData data = new ModuleRelatedRootData() {
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
             return Collections.singletonList(new ModuleAwareContentEntityIteratorImpl(myModule, entity.createPointer(),
                                                                                       IndexingRootHolder.Companion.fromFile(virtualRoot),
                                                                                       TEST_PRESENTATION));
           });
  }

  protected <T> void doTest(ThrowableComputable<? extends T, ? extends Exception> generator,
                            Function<T, ? extends Collection<IndexableFilesIterator>> expectedIteratorsProducer) throws Exception {
    super.doTest(generator, (t) -> EntityIndexingServiceOnCustomEntitiesTest.removeAllIndexingTestEntities(myProject),
                 expectedIteratorsProducer);
  }
}
