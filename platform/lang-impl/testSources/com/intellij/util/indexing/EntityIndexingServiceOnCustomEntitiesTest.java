// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.storage.EntitySource;
import com.intellij.platform.workspace.storage.EntityStorage;
import com.intellij.platform.workspace.storage.EntityStorageKt;
import com.intellij.platform.workspace.storage.MutableEntityStorage;
import com.intellij.platform.workspace.storage.url.VirtualFileUrl;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.origin.IndexingUrlRootHolder;
import com.intellij.util.indexing.roots.origin.IndexingUrlSourceRootHolder;
import com.intellij.util.indexing.testEntities.IndexingTestEntity;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar;
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleOrLibrarySourceRootData;
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleRelatedRootData;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl;
import kotlin.Unit;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.intellij.util.indexing.roots.IndexableEntityProviderMethods.INSTANCE;

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

    doTest(() -> createAndRegisterEntity(getUrls(virtualRoot), Collections.emptyList(), myProject), (entity) -> {
      return INSTANCE.createGenericContentEntityIterators(entity.createReference(),
                                                          IndexingUrlRootHolder.Companion.fromUrls(getUrls(virtualRoot)),
                                                          null);
    });
  }

  public void testAddingContentNonRecursiveWithoutModuleCustomWorkspaceEntity() throws Exception {
    registerWorkspaceFileIndexContributor((entity, registrar) -> {
      for (VirtualFileUrl root : entity.getRoots()) {
        registrar.registerNonRecursiveFileSet(root, WorkspaceFileKind.CONTENT, entity, null);
      }
    });
    File root = createTempDir("customRoot");
    VirtualFile virtualRoot = Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root.toPath()));

    doTest(() -> createAndRegisterEntity(getUrls(virtualRoot), Collections.emptyList(), myProject), (entity) -> {
      return INSTANCE.createGenericContentEntityIterators(entity.createReference(),
                                                          IndexingUrlRootHolder.Companion.fromUrls(Collections.emptyList(),
                                                                                                   getUrls(virtualRoot)),
                                                          null);
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

    doTest(() -> createAndRegisterEntity(getUrls(virtualRoot), Collections.emptyList(), myProject), (entity) -> {
      return INSTANCE.createExternalEntityIterators(entity.createReference(),
                                                    IndexingUrlSourceRootHolder.Companion.fromUrls(getUrls(virtualRoot),
                                                                                                   Collections.emptyList()),
                                                    null);
    });
  }

  public void testAddingNonRecursiveExternalCustomWorkspaceEntity() throws Exception {
    registerWorkspaceFileIndexContributor((entity, registrar) -> {
      for (VirtualFileUrl root : entity.getRoots()) {
        registrar.registerNonRecursiveFileSet(root, WorkspaceFileKind.EXTERNAL, entity, null);
      }
    });
    File root = createTempDir("customRoot");
    VirtualFile virtualRoot = Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root.toPath()));

    doTest(() -> createAndRegisterEntity(getUrls(virtualRoot), Collections.emptyList(), myProject), (entity) -> {
      return INSTANCE.createExternalEntityIterators(entity.createReference(),
                                                    IndexingUrlSourceRootHolder.Companion.fromUrls(Collections.emptyList(),
                                                                                                   getUrls(virtualRoot),
                                                                                                   Collections.emptyList(),
                                                                                                   Collections.emptyList()),
                                                    null);
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

    doTest(() -> createAndRegisterEntity(getUrls(virtualRoot), Collections.emptyList(), myProject), (entity) -> {
      return INSTANCE.createExternalEntityIterators(entity.createReference(),
                                                    IndexingUrlSourceRootHolder.Companion.fromUrls(Collections.emptyList(),
                                                                                                   getUrls(virtualRoot)),
                                                    null);
    });
  }

  public void testAddingExternalSourceNonRecursiveCustomWorkspaceEntity() throws Exception {
    registerWorkspaceFileIndexContributor((entity, registrar) -> {
      ModuleOrLibrarySourceRootData data = new ModuleOrLibrarySourceRootData() {
      };
      for (VirtualFileUrl root : entity.getRoots()) {
        registrar.registerNonRecursiveFileSet(root, WorkspaceFileKind.EXTERNAL_SOURCE, entity, data);
      }
    });
    File root = createTempDir("customRoot");
    VirtualFile virtualRoot = Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root.toPath()));

    doTest(() -> createAndRegisterEntity(getUrls(virtualRoot), Collections.emptyList(), myProject), (entity) -> {
      return INSTANCE.createExternalEntityIterators(entity.createReference(),
                                                    IndexingUrlSourceRootHolder.Companion.fromUrls(Collections.emptyList(),
                                                                                                   Collections.emptyList(),
                                                                                                   Collections.emptyList(),
                                                                                                   getUrls(virtualRoot)),
                                                    null);
    });
  }

  public void testAddingNonRecursiveModuleAwareCustomWorkspaceEntity() throws Exception {
    registerWorkspaceFileIndexContributor((entity, registrar) -> {
      ModuleRelatedRootData data = new ModuleRelatedRootData() {
        @NotNull
        @Override
        public Module getModule() {
          return myModule;
        }
      };
      for (VirtualFileUrl root : entity.getRoots()) {
        registrar.registerNonRecursiveFileSet(root, WorkspaceFileKind.CONTENT, entity, data);
      }
    });
    File root = createTempDir("customRoot");
    VirtualFile virtualRoot = Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root.toPath()));
    WriteAction.run(() -> {
      virtualRoot.createChildData(this, "childFile.txt");
    });

    doTest(() -> createAndRegisterEntity(getUrls(virtualRoot), Collections.emptyList(), myProject), (entity) -> {
      return INSTANCE.createIterators(myModule, IndexingUrlRootHolder.Companion.fromUrls(Collections.emptyList(),
                                                                                         getUrls(virtualRoot)));
    });

    try {
      WriteAction.run(() -> createAndRegisterEntity(getUrls(virtualRoot), Collections.emptyList(), myProject));

      List<VirtualFile> filesInModule = new ArrayList<>();
      ModuleRootManager.getInstance(myModule).getFileIndex().iterateContent(fileOrDir -> {
        filesInModule.add(fileOrDir);
        return true;
      });
      assertSameElements(filesInModule, Collections.singletonList(virtualRoot));
    }
    finally {
      WriteAction.run(() -> removeAllIndexingTestEntities(myProject));
    }
  }

  public void testModuleAwareCustomWorkspaceEntityALaRider() throws Exception {
    registerWorkspaceFileIndexContributor((entity, registrar) -> {
      ModuleRelatedRootData data = new ModuleRelatedRootData() {
        @NotNull
        @Override
        public Module getModule() {
          return myModule;
        }
      };
      for (VirtualFileUrl root : entity.getRoots()) {
        registrar.registerNonRecursiveFileSet(root, WorkspaceFileKind.CONTENT, entity, data);
        registrar.registerFileSet(root.append("/childDirectory"), WorkspaceFileKind.CONTENT, entity, data);
      }
    });
    File root = createTempDir("customRoot");
    VirtualFile virtualRoot = Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root.toPath()));
    WriteAction.run(() -> {
      VirtualFile childDirectory = virtualRoot.createChildDirectory(this, "childDirectory");
      childDirectory.createChildData(this, "file.txt");
    });

    doTest(() -> createAndRegisterEntity(getUrls(virtualRoot), Collections.emptyList(), myProject), (entity) -> {
      return INSTANCE.createIterators(myModule,
                                      IndexingUrlRootHolder.Companion.fromUrls(
                                        getUrls(virtualRoot.findChild("childDirectory")),
                                        getUrls(virtualRoot)));
    });

    try {
      WriteAction.run(() -> createAndRegisterEntity(getUrls(virtualRoot), Collections.emptyList(), myProject));

      List<VirtualFile> filesInModule = new ArrayList<>();
      ModuleRootManager.getInstance(myModule).getFileIndex().iterateContent(fileOrDir -> {
        filesInModule.add(fileOrDir);
        return true;
      });
      assertSameElements(filesInModule, Arrays.asList(virtualRoot,
                                                      virtualRoot.findChild("childDirectory"),
                                                      virtualRoot.findFileByRelativePath("childDirectory/file.txt")));
    }
    finally {
      WriteAction.run(() -> removeAllIndexingTestEntities(myProject));
    }
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
    IndexingTestEntity createdEntity =
      WriteAction.compute(() -> createAndRegisterEntity(getUrls(virtualRoot), getUrls(excluded), myProject));

    doTest(() -> {
      editSingleWorkspaceEntity(myProject, builder -> {
        builder.getExcludedRoots().clear();
      });
      return createdEntity;
    }, (entity) -> {
      return INSTANCE.createExternalEntityIterators(entity.createReference(),
                                                    IndexingUrlSourceRootHolder.Companion.fromUrls(getUrls(excluded),
                                                                                                   Collections.emptyList()),
                                                    null);
    });
  }

  public void testChangingExcludedRootsInCustomWorkspaceEntity() throws Exception {
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
    VirtualFile excludedBefore = WriteAction.compute(() -> virtualRoot.createChildDirectory(this, "excludedBefore"));
    VirtualFile excludedAfter = WriteAction.compute(() -> virtualRoot.createChildDirectory(this, "excludedAfter"));
    VirtualFile excludedAlways = WriteAction.compute(() -> virtualRoot.createChildDirectory(this, "excludedAlways"));
    IndexingTestEntity createdEntity =
      WriteAction.compute(() -> createAndRegisterEntity(getUrls(virtualRoot),
                                                        Arrays.asList(getUrl(excludedBefore), getUrl(excludedAlways)),
                                                        myProject));

    doTest(() -> {
      editSingleWorkspaceEntity(myProject, entityBuilder -> {
        entityBuilder.getExcludedRoots().remove(getUrl(excludedBefore));
        entityBuilder.getExcludedRoots().add(getUrl(excludedAfter));
      });
      return createdEntity;
    }, (entity) -> {
      return INSTANCE.createExternalEntityIterators(entity.createReference(),
                                                    IndexingUrlSourceRootHolder.Companion.fromUrls(getUrls(excludedBefore),
                                                                                                 Collections.emptyList()),
                                                    null);
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

    WriteAction.compute(() -> createAndRegisterEntity(getUrls(virtualRoot), getUrls(excluded), myProject));
    IndexingTestEntity otherEntity =
      WriteAction.compute(() -> createAndRegisterEntity(getUrls(child), Collections.emptyList(), myProject));

    doTestRunnables(() -> {
      editWorkspaceModel(myProject, builder -> {
        IndexingTestEntity entityWithExcludedRoot = SequencesKt.first(builder.entities(IndexingTestEntity.class),
                                                                      entity -> !entity.getExcludedRoots().isEmpty());
        builder.removeEntity(entityWithExcludedRoot);
      });
    }, () -> {
      return INSTANCE.createExternalEntityIterators(otherEntity.createReference(),
                                                    IndexingUrlSourceRootHolder.Companion.fromUrls(getUrls(excluded),
                                                                                                   Collections.emptyList()),
                                                    null);
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

  static void editSingleWorkspaceEntity(@NotNull Project project,
                                        @NotNull Consumer<? super IndexingTestEntity.Builder> modification) {
    editWorkspaceModel(project, builder -> {
      IndexingTestEntity existingEntity = SequencesKt.first(builder.entities(IndexingTestEntity.class));
      builder.modifyEntity(IndexingTestEntity.Builder.class, existingEntity, entityBuilder -> {
        modification.accept(entityBuilder);
        return Unit.INSTANCE;
      });
    });
  }

  static void editWorkspaceModel(@NotNull Project project, @NotNull Consumer<MutableEntityStorage> consumer) {
    WorkspaceModel workspaceModel = WorkspaceModel.getInstance(project);
    EntityStorage entityStorage = workspaceModel.getEntityStorage().getCurrent();
    MutableEntityStorage preliminaryBuilder = EntityStorageKt.toBuilder(EntityStorageKt.toSnapshot(entityStorage));
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
