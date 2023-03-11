// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.util.Function;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.testEntities.IndexingTestEntity;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar;
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleOrLibrarySourceRootData;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl;
import com.intellij.workspaceModel.ide.VirtualFileUrlManagerUtil;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.storage.EntitySource;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.EntityStorageKt;
import com.intellij.workspaceModel.storage.MutableEntityStorage;
import com.intellij.workspaceModel.storage.url.VirtualFileUrl;
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager;
import kotlin.Unit;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class EntityIndexingServiceOnCustomEntitiesTest extends EntityIndexingServiceTestBase {
  private static final EntitySource ENTITY_SOURCE = new EntitySource() {
    @Nullable
    @Override
    public VirtualFileUrl getVirtualFileUrl() {
      return null;
    }
  };

  public void testAddingContentWithoutModuleCustomWorkspaceEntity() throws Exception {
    registerWorkspaceFileIndexContributor((entity, registrar) -> {
      for (VirtualFileUrl root : entity.getRoots()) {
        registrar.registerFileSet(root, WorkspaceFileKind.CONTENT, entity, null);
      }
    });
    File root = createTempDir("customRoot");
    VirtualFile virtualRoot = Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root.toPath()));
    VirtualFileUrlManager fileUrlManager = VirtualFileUrlManagerUtil.getInstance(VirtualFileUrlManager.Companion, myProject);
    VirtualFileUrl url = fileUrlManager.fromUrl(virtualRoot.getUrl());

    doTest(() -> createAndRegisterEntity(Collections.singletonList(url), Collections.emptyList(), myProject),
           (entity) -> {
             return IndexableEntityProviderMethods.INSTANCE.createModuleUnawareContentEntityIterators(entity.createReference(),
                                                                                                      Collections.singletonList(
                                                                                                        virtualRoot));
           });
  }

  public void testAddingExternalCustomWorkspaceEntity() throws Exception {
    registerWorkspaceFileIndexContributor((entity, registrar) -> {
      for (VirtualFileUrl root : entity.getRoots()) {
        registrar.registerFileSet(root, WorkspaceFileKind.EXTERNAL, entity, null);
      }
    });
    File root = createTempDir("customRoot");
    VirtualFile virtualRoot = Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root.toPath()));
    VirtualFileUrlManager fileUrlManager = VirtualFileUrlManagerUtil.getInstance(VirtualFileUrlManager.Companion, myProject);
    VirtualFileUrl url = fileUrlManager.fromUrl(virtualRoot.getUrl());

    doTest(() -> createAndRegisterEntity(Collections.singletonList(url), Collections.emptyList(), myProject),
           (entity) -> {
             return IndexableEntityProviderMethods.INSTANCE.createExternalEntityIterators(entity.createReference(),
                                                                                          Collections.singletonList(virtualRoot),
                                                                                          Collections.emptyList());
           });
  }

  private void registerWorkspaceFileIndexContributor(@NotNull BiConsumer<@NotNull IndexingTestEntity, @NotNull WorkspaceFileSetRegistrar> biConsumer) {
    WorkspaceFileIndexContributor<IndexingTestEntity> contributor = new WorkspaceFileIndexContributor<>() {

      @Override
      public void registerFileSets(@NotNull IndexingTestEntity entity,
                                   @NotNull WorkspaceFileSetRegistrar registrar,
                                   @NotNull EntityStorage storage) {
        biConsumer.accept(entity, registrar);
      }

      @NotNull
      @Override
      public Class<IndexingTestEntity> getEntityClass() {
        return IndexingTestEntity.class;
      }
    };
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), WorkspaceFileIndexImpl.Companion.getEP_NAME(), contributor,
                                           getTestRootDisposable());
  }

  public void testAddingExternalSourceCustomWorkspaceEntity() throws Exception {
    registerWorkspaceFileIndexContributor((entity, registrar) -> {
      ModuleOrLibrarySourceRootData data = new ModuleOrLibrarySourceRootData() {
      };
      for (VirtualFileUrl root : entity.getRoots()) {
        registrar.registerFileSet(root, WorkspaceFileKind.EXTERNAL_SOURCE, entity, data);
      }
    });
    File root = createTempDir("customRoot");
    VirtualFile virtualRoot = Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root.toPath()));
    VirtualFileUrlManager fileUrlManager = VirtualFileUrlManagerUtil.getInstance(VirtualFileUrlManager.Companion, myProject);
    VirtualFileUrl url = fileUrlManager.fromUrl(virtualRoot.getUrl());

    doTest(() -> createAndRegisterEntity(Collections.singletonList(url), Collections.emptyList(), myProject),
           (entity) -> {
             return IndexableEntityProviderMethods.INSTANCE.createExternalEntityIterators(entity.createReference(), Collections.emptyList(),
                                                                                          Collections.singletonList(virtualRoot));
           });
  }

 public void testRemovingExcludedRootFromCustomWorkspaceEntity() throws Exception {
   registerWorkspaceFileIndexContributor((entity, registrar) -> {
     for (VirtualFileUrl root : entity.getRoots()) {
       registrar.registerFileSet(root, WorkspaceFileKind.EXTERNAL, entity, null);
     }
     for (VirtualFileUrl root : entity.getExcludedRoots()) {
       registrar.registerExcludedRoot(root, entity);
     }
   });
   File root = createTempDir("customRoot");
   VirtualFile virtualRoot = Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root.toPath()));
   VirtualFile excluded = WriteAction.compute(() -> virtualRoot.createChildDirectory(this, "excluded"));
   VirtualFileUrlManager fileUrlManager = VirtualFileUrlManagerUtil.getInstance(VirtualFileUrlManager.Companion, myProject);
   VirtualFileUrl url = fileUrlManager.fromUrl(virtualRoot.getUrl());
   IndexingTestEntity createdEntity =
     WriteAction.compute(() -> createAndRegisterEntity(Collections.singletonList(url),
                                                       Collections.singletonList(fileUrlManager.fromUrl(excluded.getUrl())), myProject));

   doTest(() -> {
     editWorkspaceModel(myProject, builder -> {
       IndexingTestEntity existingEntity = SequencesKt.first(builder.entities(IndexingTestEntity.class));
       builder.modifyEntity(IndexingTestEntity.Builder.class, existingEntity, entityBuilder -> {
         entityBuilder.getExcludedRoots().clear();
         return Unit.INSTANCE;
       });
     });
     return createdEntity;
    }, (entity) -> {
      return IndexableEntityProviderMethods.INSTANCE.createExternalEntityIterators(entity.createReference(),
                                                                                   Collections.singletonList(excluded),
                                                                                   Collections.emptyList());
    });
 }

  public void testRemovingCustomWorkspaceEntityWithExcludedRoot() throws Exception {
    registerWorkspaceFileIndexContributor((entity, registrar) -> {
      for (VirtualFileUrl root : entity.getRoots()) {
        registrar.registerFileSet(root, WorkspaceFileKind.EXTERNAL, entity, null);
      }
      for (VirtualFileUrl root : entity.getExcludedRoots()) {
        registrar.registerExcludedRoot(root, entity);
      }
    });
    File root = createTempDir("customRoot");
    VirtualFile virtualRoot = Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root.toPath()));
    VirtualFile child = WriteAction.compute(() -> virtualRoot.createChildDirectory(this, "child"));
    VirtualFile excluded = WriteAction.compute(() -> child.createChildDirectory(this, "excluded"));
    VirtualFileUrlManager fileUrlManager = VirtualFileUrlManagerUtil.getInstance(VirtualFileUrlManager.Companion, myProject);

    WriteAction.compute(() -> createAndRegisterEntity(Collections.singletonList(fileUrlManager.fromUrl(virtualRoot.getUrl())),
                                                      Collections.singletonList(fileUrlManager.fromUrl(excluded.getUrl())), myProject));
    IndexingTestEntity otherEntity =
      WriteAction.compute(() -> createAndRegisterEntity(Collections.singletonList(fileUrlManager.fromUrl(child.getUrl())),
                                                        Collections.emptyList(), myProject));

    doTestRunnables(() -> {
      editWorkspaceModel(myProject, builder -> {
        IndexingTestEntity entityWithExcludedRoot = SequencesKt.first(builder.entities(IndexingTestEntity.class),
                                                                      entity -> !entity.getExcludedRoots().isEmpty());
        builder.removeEntity(entityWithExcludedRoot);
      });
    }, () -> {
      return IndexableEntityProviderMethods.INSTANCE.createExternalEntityIterators(otherEntity.createReference(),
                                                                                   Collections.singletonList(excluded),
                                                                                   Collections.emptyList());
    });
  }

  static void removeAllIndexingTestEntities(Project project) {
    editWorkspaceModel(project, builder -> {
      List<IndexingTestEntity> entities = SequencesKt.toList(builder.entities(IndexingTestEntity.class));
      for (IndexingTestEntity testEntity : entities) {
        builder.removeEntity(testEntity);
      }
    });
  }

  @NotNull
  static IndexingTestEntity createAndRegisterEntity(List<VirtualFileUrl> roots, List<VirtualFileUrl> excludedRoots, Project project) {
    IndexingTestEntity entity = IndexingTestEntity.create(roots, excludedRoots, ENTITY_SOURCE);
    editWorkspaceModel(project, builder -> builder.addEntity(entity));
    return entity;
  }

  static void editWorkspaceModel(@NotNull Project project, @NotNull Consumer<MutableEntityStorage> consumer) {
    WorkspaceModel workspaceModel = WorkspaceModel.getInstance(project);
    EntityStorage entityStorage = workspaceModel.getEntityStorage().getCurrent();
    MutableEntityStorage preliminaryBuilder = EntityStorageKt.toBuilder(entityStorage);
    consumer.accept(preliminaryBuilder);
    workspaceModel.updateProjectModel("EntityIndexingServiceTest", storage -> {
      storage.addDiff(preliminaryBuilder);
      return Unit.INSTANCE;
    });
  }

  protected <T> void doTest(ThrowableComputable<? extends T, ? extends Exception> generator,
                            Function<T, ? extends Collection<IndexableFilesIterator>> expectedIteratorsProducer) throws Exception {
    super.doTest(generator, (t) -> removeAllIndexingTestEntities(myProject), expectedIteratorsProducer);
  }

  private void doTestRunnables(ThrowableRunnable<Exception> generator,
                               Supplier<Collection<IndexableFilesIterator>> expectedIteratorsProducer) throws Exception {
    doTest(() -> {
      generator.run();
      return null;
    }, (nothing) -> {
      return expectedIteratorsProducer.get();
    });
  }
}
