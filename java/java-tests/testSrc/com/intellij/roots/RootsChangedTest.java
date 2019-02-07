// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots;

import com.intellij.ProjectTopics;
import com.intellij.configurationStore.StateStorageManagerKt;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.ModuleTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * @author dsl
 */
public class RootsChangedTest extends ModuleTestCase {
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

    assertEventsCount(1);
    assertSameElements(ModuleRootManager.getInstance(moduleA).getContentRoots(), vDir1);

    VfsTestUtil.deleteFile(vDir1);
    assertEventsCount(1);
    assertEmpty(ModuleRootManager.getInstance(moduleA).getContentRoots());

    File dir2 = new File(root, "dir2");
    assertTrue(dir2.mkdirs());
    VirtualFile vDir2 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir2);
    assertNotNull(vDir2);

    rename(vDir2, "dir1");
    assertEventsCount(1);
    assertSameElements(ModuleRootManager.getInstance(moduleA).getContentRoots(), vDir2);

    // when the existing root is renamed, it remains a root
    rename(vDir2, "dir2");
    assertNoEvents();
    assertSameElements(ModuleRootManager.getInstance(moduleA).getContentRoots(), vDir2);

    // and event if it is moved, it's still a root
    File subdir = new File(root, "subdir");
    assertTrue(subdir.mkdirs());
    VirtualFile vSubdir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(subdir);
    assertNotNull(vSubdir);

    move(vDir2, vSubdir);
    assertEventsCount(1);
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

  public void testEditLibraryForModuleLoadFromXml() throws IOException {
    final File tempDirectory = createTempDirectory();
    ApplicationManager.getApplication().runWriteAction(() -> {
      Module a = loadModule(PathManagerEx.getHomePath(getClass()) + "/java/java-tests/testData/moduleRootManager/rootsChanged/emptyModule/a.iml");
      assertEventsCount(1);

      final Sdk jdk;
      try {
        jdk = (Sdk)IdeaTestUtil.getMockJdk17().clone();
      }
      catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
      ProjectJdkTable.getInstance().addJdk(jdk, getTestRootDisposable());
      assertNoEvents();

      ModuleRootModificationUtil.setModuleSdk(a, jdk);
      assertEventsCount(1);

      final SdkModificator sdkModificator = jdk.getSdkModificator();
      sdkModificator.addRoot(getVirtualFile(tempDirectory), OrderRootType.CLASSES);
      sdkModificator.commitChanges();
    });

    assertEventsCount(1);
  }

  public void testModuleJdkEditing() throws Exception {
    final File tempDirectory = createTempDirectory();
    ApplicationManager.getApplication().runWriteAction(() -> {
      final Module moduleA = createModule("a.iml");
      final Module moduleB = createModule("b.iml");
      assertEventsCount(2);

      final Sdk jdk;
      try {
        jdk = (Sdk)IdeaTestUtil.getMockJdk17().clone();
      }
      catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
      ProjectJdkTable.getInstance().addJdk(jdk, getTestRootDisposable());
      assertNoEvents();

      final ModifiableRootModel rootModelA = ModuleRootManager.getInstance(moduleA).getModifiableModel();
      final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
      rootModelA.setSdk(jdk);
      rootModelB.setSdk(jdk);
      ModifiableRootModel[] rootModels = {rootModelA, rootModelB};
      ModifiableModelCommitter.multiCommit(rootModels, ModuleManager.getInstance(rootModels[0].getProject()).getModifiableModel());
      assertEventsCount(1);

      final SdkModificator sdkModificator = jdk.getSdkModificator();
      sdkModificator.addRoot(getVirtualFile(tempDirectory), OrderRootType.CLASSES);
      sdkModificator.commitChanges();
      assertEventsCount(1);
    });
  }

  public void testInheritedJdkEditing() throws Exception {
    final File tempDirectory = createTempDirectory();
    ApplicationManager.getApplication().runWriteAction(() -> {
      final Module moduleA = createModule("a.iml");
      final Module moduleB = createModule("b.iml");
      assertEventsCount(2);

      final Sdk jdk;
      final Sdk jdkBBB;
      try {
        jdk = (Sdk)IdeaTestUtil.getMockJdk17("AAA").clone();
        ProjectJdkTable.getInstance().addJdk(jdk, getTestRootDisposable());
        assertNoEvents();

        jdkBBB = (Sdk)IdeaTestUtil.getMockJdk17("BBB").clone();
      }
      catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
      ProjectJdkTable.getInstance().addJdk(jdk, getTestRootDisposable());
      assertNoEvents();

      ProjectRootManager.getInstance(myProject).setProjectSdk(jdkBBB);
      assertNoEvents(true);

      final ModifiableRootModel rootModelA = ModuleRootManager.getInstance(moduleA).getModifiableModel();
      final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
      rootModelA.inheritSdk();
      rootModelB.inheritSdk();
      ModifiableRootModel[] rootModels = {rootModelA, rootModelB};
      ModifiableModelCommitter.multiCommit(rootModels, ModuleManager.getInstance(rootModels[0].getProject()).getModifiableModel());
      assertEventsCount(1);

      ProjectRootManager.getInstance(myProject).setProjectSdk(jdk);
      assertEventsCount(1);

      final SdkModificator sdkModificator = jdk.getSdkModificator();
      sdkModificator.addRoot(getVirtualFile(tempDirectory), OrderRootType.CLASSES);
      sdkModificator.commitChanges();
      assertEventsCount(1);
    });
  }

  private void verifyLibraryTableEditing(final LibraryTable libraryTable) {
    final Module moduleA = createModule("a.iml");
    final Module moduleB = createModule("b.iml");
    assertEventsCount(2);

    ApplicationManager.getApplication().runWriteAction(() -> {
      final Library libraryA = libraryTable.createLibrary("A");
      final Library.ModifiableModel libraryModifiableModel = libraryA.getModifiableModel();
      libraryModifiableModel.addRoot("file:///a", OrderRootType.CLASSES);
      libraryModifiableModel.commit();
      assertNoEvents();

      final ModifiableRootModel rootModelA = ModuleRootManager.getInstance(moduleA).getModifiableModel();
      final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
      rootModelA.addLibraryEntry(libraryA);
      rootModelB.addLibraryEntry(libraryA);
      rootModelA.addInvalidLibrary("Q", libraryTable.getTableLevel());
      rootModelB.addInvalidLibrary("Q", libraryTable.getTableLevel());
      ModifiableRootModel[] rootModels = {rootModelA, rootModelB};
      ModifiableModelCommitter.multiCommit(rootModels, ModuleManager.getInstance(rootModels[0].getProject()).getModifiableModel());
      assertEventsCount(1);

      final Library.ModifiableModel libraryModifiableModel2 = libraryA.getModifiableModel();
      final File tmpDir;
      try {
        tmpDir = FileUtil.createTempDirectory(getTestName(true), "");
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }

      try {
        final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tmpDir);
        assertNotNull(file);

        libraryModifiableModel2.addRoot(file.getUrl(), OrderRootType.CLASSES);
        libraryModifiableModel2.commit();
        assertEventsCount(1);
      }
      finally {
        FileUtil.delete(tmpDir);
      }

      libraryTable.removeLibrary(libraryA);
      assertEventsCount(1);

      final Library libraryQ = libraryTable.createLibrary("Q");
      assertEventsCount(1);

      libraryTable.removeLibrary(libraryQ);
      assertEventsCount(1);
    });
  }

  private void verifyLibraryTableEditingInUncommittedModel(final LibraryTable libraryTable) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      final Module moduleA = createModule("a.iml");
      final Module moduleB = createModule("b.iml");
      assertEventsCount(2);

      final Library libraryA = libraryTable.createLibrary("A");
      final Library.ModifiableModel libraryModifiableModel = libraryA.getModifiableModel();
      libraryModifiableModel.addRoot("file:///a", OrderRootType.CLASSES);
      libraryModifiableModel.commit();
      assertNoEvents();

      final ModifiableRootModel rootModelA = ModuleRootManager.getInstance(moduleA).getModifiableModel();
      final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
      rootModelA.addLibraryEntry(libraryA);
      rootModelB.addLibraryEntry(libraryA);
      final Library.ModifiableModel libraryModifiableModel2 = libraryA.getModifiableModel();
      libraryModifiableModel2.addRoot("file:///b", OrderRootType.CLASSES);
      libraryModifiableModel2.commit();
      assertNoEvents();

      libraryTable.removeLibrary(libraryA);
      assertNoEvents(true);

      rootModelA.addInvalidLibrary("Q", libraryTable.getTableLevel());
      rootModelB.addInvalidLibrary("Q", libraryTable.getTableLevel());
      assertNoEvents();

      final Library libraryQ = libraryTable.createLibrary("Q");
      assertNoEvents(true);

      ModifiableRootModel[] rootModels = {rootModelA, rootModelB};
      ModifiableModelCommitter.multiCommit(rootModels, ModuleManager.getInstance(rootModels[0].getProject()).getModifiableModel());
      assertEventsCount(1);

      libraryTable.removeLibrary(libraryQ);
      assertEventsCount(1);
    });
  }

  private void assertNoEvents() {
    assertNoEvents(false);
  }

  private void assertNoEvents(boolean modificationCountMustBeIncremented) {
    assertEventsCountAndIncrementModificationCount(0, modificationCountMustBeIncremented);
  }

  private void assertEventsCount(int count) {
    assertEventsCountAndIncrementModificationCount(count, count != 0);
  }

  private void assertEventsCountAndIncrementModificationCount(int eventsCount, boolean modificationCountMustBeIncremented) {
    final int beforeCount = myModuleRootListener.beforeCount;
    final int afterCount = myModuleRootListener.afterCount;
    assertEquals("beforeCount = " + beforeCount + ", afterCount = " + afterCount, beforeCount, afterCount);
    assertEquals(eventsCount, beforeCount);
    long currentModificationCount = ProjectRootManager.getInstance(myProject).getModificationCount();
    if (modificationCountMustBeIncremented) {
      assertTrue(currentModificationCount > myModuleRootListener.modificationCount);
    }
    else {
      assertEquals(myModuleRootListener.modificationCount, currentModificationCount);
    }
    myModuleRootListener.reset();
  }

  private static class MyModuleRootListener implements ModuleRootListener {
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

    private void reset() {
      beforeCount = 0;
      afterCount = 0;
      modificationCount = ProjectRootManager.getInstance(myProject).getModificationCount();
    }
  }

  public void testRootsChangedPerformanceInPresenceOfManyVirtualFilePointers() throws Exception {
    VirtualFile temp = LocalFileSystem.getInstance().findFileByIoFile(createTempDirectory());
    String dirName = "xxx";
    for (int i = 0; i < 10_000; i++) {
      VirtualFilePointerManager.getInstance().create(temp.getUrl() + "/" + dirName + "/" + i, getTestRootDisposable(), null);
    }

    VirtualFile xxx = createChildDirectory(temp, dirName);

    PlatformTestUtil.startPerformanceTest("time wasted in ProjectRootManagerComponent.before/afterValidityChanged()", 10000, ()->{
      for (int i = 0; i < 100; i++) {
        rename(xxx, "yyy");
        rename(xxx, dirName);
      }
    }).assertTiming();
  }

  @NotNull
  @Override
  protected Path getProjectDirOrFile() {
    // create ".idea" - based project because it's 1) needed for testShelveChangesMustNotLeadToRootsChangedEvent and 2) is more common
    return getProjectDirOrFile(true);
  }

  public void testShelveChangesMustNotLeadToRootsChangedEvent() {
    // create .idea
    StateStorageManagerKt.saveComponentManager(getProject());
    VirtualFile shelf = createChildDirectory(getProject().getProjectFile().getParent(), "shelf");

    myModuleRootListener.reset();

    VirtualFile xxx = createChildData(shelf, "shelf1.dat");
    assertTrue(ChangeListManager.getInstance(myProject).isPotentiallyIgnoredFile(xxx));

    assertEquals(myModuleRootListener.modificationCount, ProjectRootManager.getInstance(myProject).getModificationCount());

    VirtualFile newShelf = createChildDirectory(getProject().getBaseDir().getParent(), "newShelf");
    VcsConfiguration vcs = VcsConfiguration.getInstance(myProject);
    vcs.USE_CUSTOM_SHELF_PATH = true;
    vcs.CUSTOM_SHELF_PATH = newShelf.getPath();
    myModuleRootListener.reset();

    VirtualFile xxx2 = createChildData(newShelf, "shelf1.dat");
    assertTrue(ChangeListManager.getInstance(myProject).isPotentiallyIgnoredFile(xxx2));
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
    assertEventsCount(mustGenerateEvents);

    myModuleRootListener.reset();
    UIUtil.dispatchAllInvocationEvents();
    delete(dir);
    assertEventsCount(mustGenerateEvents);
  }
}
