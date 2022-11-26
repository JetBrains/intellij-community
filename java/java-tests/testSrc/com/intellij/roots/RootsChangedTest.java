// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.roots;

import com.intellij.ProjectTopics;
import com.intellij.configurationStore.StoreUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.VcsIgnoreManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.testFramework.JavaModuleTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.SimpleMessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Checks that proper {@link ModuleRootListener#rootsChanged} events are sent. Consider adding new tests to {@link LibraryRootsChangedTest}
 * or {@link ModuleRootsChangedTest} which use more convenient API.
 */
public class RootsChangedTest extends JavaModuleTestCase {
  private MyModuleRootListener myModuleRootListener;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    getOrCreateProjectBaseDir();
    MessageBusConnection connection = myProject.getMessageBus().connect(getTestRootDisposable());
    myModuleRootListener = new MyModuleRootListener(myProject);
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, myModuleRootListener);
  }

  @Override
  protected void tearDown() throws Exception {
    myModuleRootListener = null;
    super.tearDown();
  }

  public void testEventsAfterFileModifications() {
    File root = new File(FileUtil.getTempDirectory());

    File dir1 = new File(root, "dir1");
    assertTrue(dir1.mkdirs());
    VirtualFile vDir1 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir1);
    assertNotNull(vDir1);

    Module moduleA = createModule("a.iml");
    myModuleRootListener.reset();

    ModuleRootModificationUtil.addContentRoot(moduleA, vDir1.getPath());
    UIUtil.dispatchAllInvocationEvents();

    myModuleRootListener.assertEventsCount(1);
    assertSameElements(ModuleRootManager.getInstance(moduleA).getContentRoots(), vDir1);

    VfsTestUtil.deleteFile(vDir1);
    myModuleRootListener.assertEventsCount(1);
    assertEmpty(ModuleRootManager.getInstance(moduleA).getContentRoots());

    File dir2 = new File(root, "dir2");
    assertTrue(dir2.mkdirs());
    VirtualFile vDir2 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir2);
    assertNotNull(vDir2);

    rename(vDir2, "dir1");
    myModuleRootListener.assertEventsCount(1);
    assertSameElements(ModuleRootManager.getInstance(moduleA).getContentRoots(), vDir2);

    // when the existing root is renamed, it remains a root
    rename(vDir2, "dir2");
    myModuleRootListener.assertEventsCount(1);
    assertSameElements(ModuleRootManager.getInstance(moduleA).getContentRoots(), vDir2);

    // and event if it is moved, it's still a root
    File subdir = new File(root, "subdir");
    assertTrue(subdir.mkdirs());
    VirtualFile vSubdir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(subdir);
    assertNotNull(vSubdir);

    move(vDir2, vSubdir);
    myModuleRootListener.assertEventsCount(1);
    assertSameElements(ModuleRootManager.getInstance(moduleA).getContentRoots(), vDir2);
  }

  public void testProjectLibraryChangeEvent() {
    final LibraryTable projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
    verifyLibraryTableEditing(projectLibraryTable);
  }

  public void testGlobalLibraryChangeEvent() {
    final LibraryTable globalLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable();
    verifyLibraryTableEditing(globalLibraryTable);
  }

  public void testProjectLibraryEventsInUncommittedModel() {
    final LibraryTable projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
    verifyLibraryTableEditingInUncommittedModel(projectLibraryTable);
  }

  public void testGlobalLibraryEventsInUncommittedModel() {
    final LibraryTable globalLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable();
    verifyLibraryTableEditingInUncommittedModel(globalLibraryTable);
  }

  public void testEditLibraryForModuleLoadFromXml() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      Module a = loadModule(Paths.get(PathManagerEx.getHomePath(getClass())).resolve("java/java-tests/testData/moduleRootManager/rootsChanged/emptyModule/a.iml"));
      myModuleRootListener.assertEventsCount(1);

      final Sdk jdk = ProjectJdkTable.getInstance().createSdk("new-jdk", JavaSdk.getInstance());
      ProjectJdkTable.getInstance().addJdk(jdk, getTestRootDisposable());
      myModuleRootListener.assertNoEvents();

      ModuleRootModificationUtil.setModuleSdk(a, jdk);
      myModuleRootListener.assertEventsCount(1);

      SdkModificator sdkModificator = jdk.getSdkModificator();
      sdkModificator.addRoot(getTempDir().createVirtualDir(), OrderRootType.CLASSES);
      sdkModificator.commitChanges();
    });

    myModuleRootListener.assertEventsCount(1);
  }

  public void testModuleJdkEditing() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      final Module moduleA = createModule("a.iml");
      final Module moduleB = createModule("b.iml");
      myModuleRootListener.assertEventsCount(2);

      final Sdk jdk = ProjectJdkTable.getInstance().createSdk("new-jdk", JavaSdk.getInstance());
      final Sdk unused = ProjectJdkTable.getInstance().createSdk("unused", JavaSdk.getInstance());
      ProjectJdkTable.getInstance().addJdk(jdk, getTestRootDisposable());
      ProjectJdkTable.getInstance().addJdk(unused, getTestRootDisposable());
      myModuleRootListener.assertNoEvents();

      final ModifiableRootModel rootModelA = ModuleRootManager.getInstance(moduleA).getModifiableModel();
      final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
      rootModelA.setSdk(jdk);
      rootModelB.setSdk(jdk);
      ModifiableRootModel[] rootModels = {rootModelA, rootModelB};
      ModifiableModelCommitter.multiCommit(rootModels, ModuleManager.getInstance(rootModels[0].getProject()).getModifiableModel());
      myModuleRootListener.assertEventsCount(1);

      final SdkModificator sdkModificator = jdk.getSdkModificator();
      sdkModificator.addRoot(getTempDir().createVirtualDir(), OrderRootType.CLASSES);
      sdkModificator.commitChanges();
      myModuleRootListener.assertEventsCount(1);

      final SdkModificator sdkModificator2 = unused.getSdkModificator();
      sdkModificator2.addRoot(getTempDir().createVirtualDir(), OrderRootType.CLASSES);
      sdkModificator2.commitChanges();
      myModuleRootListener.assertNoEvents();
    });
  }

  public void testSetupUnknownJdk() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      Module module = createModule("a.iml");
      myModuleRootListener.assertEventsCount(1);
      ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
      model.setInvalidSdk("new-jdk", JavaSdk.getInstance().getName());
      model.commit();
      myModuleRootListener.assertEventsCount(1);

      Sdk unusedJdk = ProjectJdkTable.getInstance().createSdk("unused-jdk", JavaSdk.getInstance());
      ProjectJdkTable.getInstance().addJdk(unusedJdk, getTestRootDisposable());
      myModuleRootListener.assertNoEvents();
      
      Sdk jdk = ProjectJdkTable.getInstance().createSdk("new-jdk", JavaSdk.getInstance());
      ProjectJdkTable.getInstance().addJdk(jdk, getTestRootDisposable());
      myModuleRootListener.assertEventsCount(1);

      final SdkModificator sdkModificator = jdk.getSdkModificator();
      sdkModificator.addRoot(getTempDir().createVirtualDir(), OrderRootType.CLASSES);
      sdkModificator.commitChanges();
      myModuleRootListener.assertEventsCount(1);
    });
  }

  public void testInheritedJdkEditing() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      final Module moduleA = createModule("a.iml");
      final Module moduleB = createModule("b.iml");
      myModuleRootListener.assertEventsCount(2);

      final Sdk jdk;
      final Sdk jdkBBB;
      jdk = ProjectJdkTable.getInstance().createSdk("AAA", JavaSdk.getInstance());
      ProjectJdkTable.getInstance().addJdk(jdk, getTestRootDisposable());
      myModuleRootListener.assertNoEvents();

      jdkBBB = ProjectJdkTable.getInstance().createSdk("BBB", JavaSdk.getInstance());
      ProjectJdkTable.getInstance().addJdk(jdk, getTestRootDisposable());
      myModuleRootListener.assertNoEvents();

      ProjectRootManager.getInstance(myProject).setProjectSdk(jdkBBB);
      myModuleRootListener.assertNoEvents(true);

      final ModifiableRootModel rootModelA = ModuleRootManager.getInstance(moduleA).getModifiableModel();
      final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
      rootModelA.inheritSdk();
      rootModelB.inheritSdk();
      ModifiableRootModel[] rootModels = {rootModelA, rootModelB};
      ModifiableModelCommitter.multiCommit(rootModels, ModuleManager.getInstance(rootModels[0].getProject()).getModifiableModel());
      myModuleRootListener.assertEventsCount(1);

      ProjectRootManager.getInstance(myProject).setProjectSdk(jdk);
      myModuleRootListener.assertEventsCount(1);

      final SdkModificator sdkModificator = jdk.getSdkModificator();
      sdkModificator.addRoot(getTempDir().createVirtualDir(), OrderRootType.CLASSES);
      sdkModificator.commitChanges();
      myModuleRootListener.assertEventsCount(1);
    });
  }

  private void verifyLibraryTableEditing(final LibraryTable libraryTable) {
    final Module moduleA = createModule("a.iml");
    final Module moduleB = createModule("b.iml");
    myModuleRootListener.assertEventsCount(2);

    ApplicationManager.getApplication().runWriteAction(() -> {
      final Library libraryA = libraryTable.createLibrary("A");
      final Library.ModifiableModel libraryModifiableModel = libraryA.getModifiableModel();
      libraryModifiableModel.addRoot(someAbsoluteUrl("a"), OrderRootType.CLASSES);
      libraryModifiableModel.commit();
      myModuleRootListener.assertNoEvents();

      final ModifiableRootModel rootModelA = ModuleRootManager.getInstance(moduleA).getModifiableModel();
      final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
      rootModelA.addLibraryEntry(libraryA);
      rootModelB.addLibraryEntry(libraryA);
      rootModelA.addInvalidLibrary("Q", libraryTable.getTableLevel());
      rootModelB.addInvalidLibrary("Q", libraryTable.getTableLevel());
      ModifiableRootModel[] rootModels = {rootModelA, rootModelB};
      ModifiableModelCommitter.multiCommit(rootModels, ModuleManager.getInstance(rootModels[0].getProject()).getModifiableModel());
      myModuleRootListener.assertEventsCount(1);

      Library.ModifiableModel libraryModifiableModel2 = libraryA.getModifiableModel();
      VirtualFile file = getTempDir().createVirtualDir();
      libraryModifiableModel2.addRoot(file.getUrl(), OrderRootType.CLASSES);
      libraryModifiableModel2.commit();
      myModuleRootListener.assertEventsCount(1);

      libraryTable.removeLibrary(libraryA);
      myModuleRootListener.assertEventsCount(1);

      final Library libraryQ = libraryTable.createLibrary("Q");
      myModuleRootListener.assertEventsCount(1);

      Library.ModifiableModel model = libraryQ.getModifiableModel();
      model.addRoot(getTempDir().createVirtualDir(), OrderRootType.CLASSES);
      model.commit();
      myModuleRootListener.assertEventsCount(1);

      libraryTable.removeLibrary(libraryQ);
      myModuleRootListener.assertEventsCount(1);
    });
  }

  @NotNull
  private static String someAbsoluteUrl(@NotNull String name) {
    return SystemInfo.isUnix ? "file:///" + name : "file://C:/"+name;
  }

  private void verifyLibraryTableEditingInUncommittedModel(LibraryTable libraryTable) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      final Module moduleA = createModule("a.iml");
      final Module moduleB = createModule("b.iml");
      myModuleRootListener.assertEventsCount(2);

      final Library libraryA = libraryTable.createLibrary("A");
      final Library.ModifiableModel libraryModifiableModel = libraryA.getModifiableModel();
      libraryModifiableModel.addRoot(someAbsoluteUrl("a"), OrderRootType.CLASSES);
      libraryModifiableModel.commit();
      myModuleRootListener.assertNoEvents();

      final ModifiableRootModel rootModelA = ModuleRootManager.getInstance(moduleA).getModifiableModel();
      final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
      rootModelA.addLibraryEntry(libraryA);
      rootModelB.addLibraryEntry(libraryA);
      final Library.ModifiableModel libraryModifiableModel2 = libraryA.getModifiableModel();
      libraryModifiableModel2.addRoot(someAbsoluteUrl("b"), OrderRootType.CLASSES);
      libraryModifiableModel2.commit();
      myModuleRootListener.assertNoEvents();

      libraryTable.removeLibrary(libraryA);
      myModuleRootListener.assertNoEvents(true);

      rootModelA.addInvalidLibrary("Q", libraryTable.getTableLevel());
      rootModelB.addInvalidLibrary("Q", libraryTable.getTableLevel());
      myModuleRootListener.assertNoEvents();

      final Library libraryQ = libraryTable.createLibrary("Q");
      myModuleRootListener.assertNoEvents(true);

      ModifiableRootModel[] rootModels = {rootModelA, rootModelB};
      ModifiableModelCommitter.multiCommit(rootModels, ModuleManager.getInstance(rootModels[0].getProject()).getModifiableModel());
      myModuleRootListener.assertEventsCount(1);

      libraryTable.removeLibrary(libraryQ);
      myModuleRootListener.assertEventsCount(1);
    });
  }

  static class MyModuleRootListener implements ModuleRootListener {
    private final Project myProject;
    private int beforeCount;
    private int afterCount;
    private long modificationCount;

    MyModuleRootListener(Project project) {
      myProject = project;
    }

    @Override
    public void beforeRootsChange(@NotNull ModuleRootEvent event) {
      beforeCount++;
    }

    @Override
    public void rootsChanged(@NotNull ModuleRootEvent event) {
      afterCount++;
    }

    void reset() {
      beforeCount = 0;
      afterCount = 0;
      modificationCount = ProjectRootManager.getInstance(myProject).getModificationCount();
    }

    void assertEventsCountAndIncrementModificationCount(int eventsCount,
                                                        boolean modificationCountMustBeIncremented,
                                                        boolean modificationCountMayBeIncremented) {
      final int beforeCount = this.beforeCount;
      final int afterCount = this.afterCount;
      assertEquals("beforeCount = " + beforeCount + ", afterCount = " + afterCount, beforeCount, afterCount);
      assertEquals(eventsCount, beforeCount);
      long currentModificationCount = ProjectRootManager.getInstance(myProject).getModificationCount();
      if (modificationCountMayBeIncremented) {
        assertTrue(currentModificationCount >= modificationCount);
      }
      else if (modificationCountMustBeIncremented) {
        assertTrue(currentModificationCount > modificationCount);
      }
      else {
        assertEquals(modificationCount, currentModificationCount);
      }
      reset();
    }

    void assertNoEvents(boolean modificationCountMayBeIncremented) {
      assertEventsCountAndIncrementModificationCount(0, false, modificationCountMayBeIncremented);
    }

    void assertEventsCount(int count) {
      assertEventsCountAndIncrementModificationCount(count, count != 0, false);
    }

    void assertNoEvents() {
      assertNoEvents(false);
    }
  }

  public void testRootsChangedPerformanceInPresenceOfManyVirtualFilePointers() {
    VirtualFile temp = getTempDir().createVirtualDir();
    String dirName = "xxx";
    VirtualFilePointerManager virtualFilePointerManager = VirtualFilePointerManager.getInstance();
    for (int i = 0; i < 10_000; i++) {
      virtualFilePointerManager.create(temp.getUrl() + "/" + dirName + "/" + i, getTestRootDisposable(), null);
    }

    VirtualFile xxx = createChildDirectory(temp, dirName);

    PlatformTestUtil.startPerformanceTest("time wasted in ProjectRootManagerComponent.before/afterValidityChanged()", 10000, ()->{
      for (int i = 0; i < 100; i++) {
        rename(xxx, "yyy");
        rename(xxx, dirName);
      }
    }).assertTiming();
  }

  // create ".idea" - based project because it's 1) needed for testShelveChangesMustNotLeadToRootsChangedEvent and 2) is more common
  @Override
  protected boolean isCreateDirectoryBasedProject() {
    return true;
  }

  public void testShelveChangesMustNotLeadToRootsChangedEvent() {
    // create .idea
    StoreUtil.saveSettings(getProject());
    VirtualFile shelf = createChildDirectory(getProject().getProjectFile().getParent(), "shelf");
    VcsIgnoreManager vcsIgnoreManager = VcsIgnoreManager.getInstance(myProject);

    myModuleRootListener.reset();

    VirtualFile xxx = createChildData(shelf, "shelf1.dat");
    assertTrue(vcsIgnoreManager.isPotentiallyIgnoredFile(xxx));

    assertEquals(myModuleRootListener.modificationCount, ProjectRootManager.getInstance(myProject).getModificationCount());

    VirtualFile newShelf = createChildDirectory(getOrCreateProjectBaseDir().getParent(), "newShelf");
    VcsConfiguration vcs = VcsConfiguration.getInstance(myProject);
    vcs.USE_CUSTOM_SHELF_PATH = true;
    vcs.CUSTOM_SHELF_PATH = newShelf.getPath();
    myModuleRootListener.reset();

    VirtualFile xxx2 = createChildData(newShelf, "shelf1.dat");
    assertTrue(vcsIgnoreManager.isPotentiallyIgnoredFile(xxx2));
  }

  public void testCreationDeletionOfRootDirectoriesMustLeadToRootsChanged() {
    File root = new File(FileUtil.getTempDirectory());

    File dir1 = new File(root, "dir1");
    assertTrue(dir1.mkdirs());
    VirtualFile contentRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir1);
    assertNotNull(contentRoot);

    Module moduleA = createModule("a.iml");
    ModuleRootModificationUtil.addContentRoot(moduleA, contentRoot.getPath());
    String excludedUrl = contentRoot.getUrl() + "/excluded";
    String sourceUrl = contentRoot.getUrl() + "/src";
    String testSourceUrl = contentRoot.getUrl() + "/testSrc";
    String outputUrl = contentRoot.getUrl() + "/out";
    String testOutputUrl = contentRoot.getUrl() + "/testOut";
    String resourceUrl = contentRoot.getUrl() + "/res";
    String testResourceUrl = contentRoot.getUrl() + "/testRes";

    ModuleRootModificationUtil.updateModel(moduleA, model -> {
      ContentEntry entry = ContainerUtil.find(model.getContentEntries(), e-> contentRoot.equals(e.getFile()));

      entry.addExcludeFolder(excludedUrl);
      entry.addSourceFolder(sourceUrl, false);
      entry.addSourceFolder(testSourceUrl, true);
      entry.addSourceFolder(resourceUrl, JavaResourceRootType.RESOURCE);
      entry.addSourceFolder(testResourceUrl, JavaResourceRootType.TEST_RESOURCE);

      model.getModuleExtension(CompilerModuleExtension.class).setCompilerOutputPath(outputUrl);
      model.getModuleExtension(CompilerModuleExtension.class).setCompilerOutputPathForTests(testOutputUrl);
    });

    checkRootChangedOnDirCreationDeletion(contentRoot, excludedUrl, 1);
    checkRootChangedOnDirCreationDeletion(contentRoot, sourceUrl, 1);
    checkRootChangedOnDirCreationDeletion(contentRoot, testSourceUrl, 1);
    checkRootChangedOnDirCreationDeletion(contentRoot, resourceUrl, 1);
    checkRootChangedOnDirCreationDeletion(contentRoot, testResourceUrl, 1);
    checkRootChangedOnDirCreationDeletion(contentRoot, outputUrl, 1);
    checkRootChangedOnDirCreationDeletion(contentRoot, testOutputUrl, 1);
    checkRootChangedOnDirCreationDeletion(contentRoot, "xxx", 0);
  }

  private void checkRootChangedOnDirCreationDeletion(VirtualFile contentRoot, String dirUrl, int mustGenerateEvents) {
    myModuleRootListener.reset();
    UIUtil.dispatchAllInvocationEvents();

    VirtualFile dir = createChildDirectory(contentRoot, new File(dirUrl).getName());
    myModuleRootListener.assertEventsCount(mustGenerateEvents);

    myModuleRootListener.reset();
    UIUtil.dispatchAllInvocationEvents();
    delete(dir);
    myModuleRootListener.assertEventsCount(mustGenerateEvents);
  }

  public void testEmptyDirectoryCreatedAndSomeRogueFileListenerImmediatelyCallsGetChildrenPreventingFurtherCreationEventsForFilesThatHappenedToBeAlreadyThereByThatMoment() {
    File ioRoot = new File(FileUtil.getTempDirectory());

    VirtualFile vRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioRoot);
    vRoot.getChildren();

    Module module = createModule("a.iml");
    ModuleRootModificationUtil.addContentRoot(module, vRoot.getPath());
    ModuleRootModificationUtil.updateModel(module, model -> model.getContentEntries()[0].addExcludeFolder(vRoot.getUrl() + "/parent/excluded"));

    MessageBusConnection connect = ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable());
    BulkFileListener rogueListenerWhichStupidlyGetChildrenRightAway = new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        for (VFileEvent event : events) {
          VirtualFile file = event.getFile();
          if (event instanceof VFileCreateEvent && file != null) {
            file.getChildren();// aaaaaaaaah!
          }
        }
      }
    };
    connect.subscribe(VirtualFileManager.VFS_CHANGES, rogueListenerWhichStupidlyGetChildrenRightAway);

    myModuleRootListener.reset();

    File iParent = new File(ioRoot, "parent");
    assertTrue(iParent.mkdirs());
    vRoot.refresh(true, true);

    TimeoutUtil.sleep(1000); // hope that now async refresh has found "parent" and is waiting for EDT to fire events

    File ioExcluded = new File(iParent, "excluded");
    assertTrue(ioExcluded.mkdirs());
    UIUtil.dispatchAllInvocationEvents(); // now events are fired

    assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioExcluded));

    myModuleRootListener.assertEventsCount(1);
  }

  public void testChangesInsideCompilerOutputDirectoryMustNotLeadToRootsChange() {
    File temp = new File(FileUtil.getTempDirectory());
    VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(temp);
    VirtualFile content = VfsTestUtil.createDir(root, "content");

    Module module = createModule("a.iml");
    ModuleRootModificationUtil.addContentRoot(module, content.getPath());
    VirtualFile out = VfsTestUtil.createDir(root, "out");

    ModuleRootModificationUtil.updateModel(module, model -> {
      CompilerModuleExtension moduleExtension = model.getModuleExtension(CompilerModuleExtension.class);
      moduleExtension.inheritCompilerOutputPath(false);
      moduleExtension.setCompilerOutputPath(out);
      moduleExtension.setCompilerOutputPathForTests(out);
    });

    myModuleRootListener.reset();
    VirtualFile f1 = VfsTestUtil.createFile(out, "x/x.txt");
    VirtualFile f2 = VfsTestUtil.createFile(out, "x/x.class");
    myModuleRootListener.assertEventsCount(0);
    VfsTestUtil.deleteFile(f1);
    VfsTestUtil.deleteFile(f2);
    myModuleRootListener.assertEventsCount(0);
  }

  public void testBulkRootsChanging() {
    WriteAction.run(() -> {
      myModuleRootListener.reset();
      ProjectRootManagerEx.getInstanceEx(myProject).mergeRootsChangesDuring(() -> {
        for (int i = 0; i < 10; i++) {
          ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
          try {
            File dir = createTempDir("src-" + i);
            VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
            model.addContentEntry(vDir);
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
          model.commit();

          assertEquals(1, myModuleRootListener.beforeCount);
          assertEquals(0, myModuleRootListener.afterCount);
        }
      });
      myModuleRootListener.assertEventsCount(1);
    });
  }

  public void testRootDirDeletionDoesntLeadToIndexing() throws IOException {
    File contentRoot = createTempDir("content-root");
    File excludedRoot = new File(contentRoot, "excluded-root");
    assertTrue(excludedRoot.mkdirs());
    VirtualFile excludedRootVFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(excludedRoot);

    ModuleRootModificationUtil.updateModel(myModule, model -> model.addContentEntry(excludedRootVFile.getParent()).addExcludeFolder(excludedRootVFile));

    AtomicInteger dumbModeCount = new AtomicInteger();
    SimpleMessageBusConnection connection = myProject.getMessageBus().simpleConnect();
    try {
      connection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
        @Override
        public void enteredDumbMode() {
          dumbModeCount.incrementAndGet();
        }
      });

      FileUtil.delete(excludedRoot);
      excludedRootVFile.refresh(false, true);
      assertFalse(excludedRootVFile.isValid());
      assertEquals(0, dumbModeCount.get());
    }
    finally {
      connection.disconnect();
    }
  }

  public void testRootsChangeEventAtCreatingAndMoveFolderUnderJarDir() throws IOException {
    String rootJarDirName = "jarDir";

    File temp = new File(FileUtil.getTempDirectory());
    VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(temp);
    VirtualFile jarDir = VfsTestUtil.createDir(root, rootJarDirName);

    LibraryTable projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
    LibraryTable.ModifiableModel projectLibraryTableModifiableModel = projectLibraryTable.getModifiableModel();
    Library projectLib = projectLibraryTableModifiableModel.createLibrary("lib");
    Library.ModifiableModel modifiableModel = projectLib.getModifiableModel();
    modifiableModel.addJarDirectory(jarDir, false);
    WriteAction.runAndWait(() -> {
      modifiableModel.commit();
      projectLibraryTableModifiableModel.commit();
    });

    myModuleRootListener.reset();
    // Creating simple file - first event
    VfsTestUtil.createFile(jarDir, "test.txt");
    // Creating folder - second event
    VirtualFile newVirtualDirectory = VfsTestUtil.createDir(jarDir, "foo");
    // Recursive folder creating - third event
    VfsTestUtil.createDir(newVirtualDirectory, "bar");
    myModuleRootListener.assertEventsCount(3);

    VirtualFile otherFolder = VfsTestUtil.createDir(root, "baz");
    myModuleRootListener.assertNoEvents();
    WriteAction.runAndWait(() -> otherFolder.move(this, newVirtualDirectory));
    myModuleRootListener.assertEventsCount(1);
  }

  public void testRootsChangeEventAtRenameCopyAndDeleteFileUnderJarDir() throws IOException {
    String rootJarDirName = "jarDir";

    File temp = new File(FileUtil.getTempDirectory());
    VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(temp);
    VirtualFile testFileUnderRoot = VfsTestUtil.createFile(root, "test.jar");

    VirtualFile jarDir = VfsTestUtil.createDir(root, rootJarDirName);
    VirtualFile testFileUnderJarDir = VfsTestUtil.createFile(jarDir, "test.jar");

    LibraryTable projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
    LibraryTable.ModifiableModel projectLibraryTableModifiableModel = projectLibraryTable.getModifiableModel();
    Library projectLib = projectLibraryTableModifiableModel.createLibrary("lib");
    Library.ModifiableModel modifiableModel = projectLib.getModifiableModel();
    modifiableModel.addJarDirectory(jarDir, false);
    WriteAction.runAndWait(() -> {
      modifiableModel.commit();
      projectLibraryTableModifiableModel.commit();
    });

    myModuleRootListener.reset();
    WriteAction.runAndWait(() -> testFileUnderJarDir.rename(this, "test2.jar"));
    myModuleRootListener.assertEventsCount(1);
    WriteAction.runAndWait(() -> testFileUnderJarDir.delete(this));
    myModuleRootListener.assertEventsCount(1);

    WriteAction.runAndWait(() -> testFileUnderRoot.copy(this, jarDir, "test2.jar"));
    myModuleRootListener.assertEventsCount(1);
  }
}
