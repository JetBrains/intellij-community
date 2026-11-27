// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.roots;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.configurationStore.StoreUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LibraryRootsChangedTest;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.VcsIgnoreManager;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.HeavyTestHelper;
import com.intellij.testFramework.ModuleRootEventTracker;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.SimpleMessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.createChildData;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.createChildDirectory;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.move;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.rename;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that proper {@link ModuleRootListener#rootsChanged} events are sent. Consider adding new tests to {@link LibraryRootsChangedTest}
 * or {@link ModuleRootsChangedTest} which use more convenient API.
 */
@TestApplication
public class RootsChangedTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  private ModuleRootEventTracker myModuleRootListener;

  @BeforeEach
  void setUp() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();

    HeavyTestHelper.getOrCreateProjectBaseDir(fixture.getProject());
    MessageBusConnection connection = fixture.getProject().getMessageBus().connect(fixture.getTestRootDisposable());
    myModuleRootListener = new ModuleRootEventTracker(fixture.getProject());
    connection.subscribe(ModuleRootListener.TOPIC, myModuleRootListener);
  }

  @Test
  public void testEventsAfterFileModifications() {
    File root = heavyFixture.get().createTempVirtualDir().toNioPath().toFile();

    File dir1 = new File(root, "dir1");
    assertTrue(dir1.mkdirs());
    VirtualFile vDir1 = StandardFileSystems.local().refreshAndFindFileByPath(dir1.getAbsolutePath());
    assertNotNull(vDir1);

    Module moduleA = heavyFixture.get().createModule("a.iml");
    myModuleRootListener.reset();

    ModuleRootModificationUtil.addContentRoot(moduleA, vDir1.getPath());
    EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());

    myModuleRootListener.assertEventsCount(1);
    assertThat(ModuleRootManager.getInstance(moduleA).getContentRoots()).containsExactlyInAnyOrder(vDir1);

    VfsTestUtil.deleteFile(vDir1);
    myModuleRootListener.assertEventsCount(1);
    assertThat(ModuleRootManager.getInstance(moduleA).getContentRoots()).isEmpty();

    File dir2 = new File(root, "dir2");
    assertTrue(dir2.mkdirs());
    VirtualFile vDir2 = StandardFileSystems.local().refreshAndFindFileByPath(dir2.getAbsolutePath());
    assertNotNull(vDir2);

    rename(vDir2, "dir1");
    myModuleRootListener.assertEventsCount(1);
    assertThat(ModuleRootManager.getInstance(moduleA).getContentRoots()).containsExactlyInAnyOrder(vDir2);

    // when the existing root is renamed, it remains a root
    rename(vDir2, "dir2");
    myModuleRootListener.assertEventsCount(1);
    assertThat(ModuleRootManager.getInstance(moduleA).getContentRoots()).containsExactlyInAnyOrder(vDir2);

    // and event if it is moved, it's still a root
    File subdir = new File(root, "subdir");
    assertTrue(subdir.mkdirs());
    VirtualFile vSubdir = StandardFileSystems.local().refreshAndFindFileByPath(subdir.getAbsolutePath());
    assertNotNull(vSubdir);

    move(vDir2, vSubdir);
    myModuleRootListener.assertEventsCount(1);
    assertThat(ModuleRootManager.getInstance(moduleA).getContentRoots()).containsExactlyInAnyOrder(vDir2);
  }

  @Test
  public void testProjectLibraryChangeEvent() {
    final LibraryTable projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(heavyFixture.get().getProject());
    verifyLibraryTableEditing(projectLibraryTable);
  }

  @Test
  public void testGlobalLibraryChangeEvent() {
    final LibraryTable globalLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable();
    verifyLibraryTableEditing(globalLibraryTable);
  }

  @Test
  public void testProjectLibraryEventsInUncommittedModel() {
    final LibraryTable projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(heavyFixture.get().getProject());
    verifyLibraryTableEditingInUncommittedModel(projectLibraryTable);
  }

  @Test
  public void testGlobalLibraryEventsInUncommittedModel() {
    final LibraryTable globalLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable();
    verifyLibraryTableEditingInUncommittedModel(globalLibraryTable);
  }

  @Test
  public void testEditLibraryForModuleLoadFromXml() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    WriteAction.runAndWait(() -> {
      Module a = fixture.loadModule(Paths.get(JavaTestUtil.getJavaTestDataPath()).resolve("moduleRootManager/rootsChanged/emptyModule/a.iml"));
      myModuleRootListener.assertEventsCount(1);

      final Sdk jdk = ProjectJdkTable.getInstance().createSdk("new-jdk", JavaSdk.getInstance());
      ProjectJdkTable.getInstance().addJdk(jdk, fixture.getTestRootDisposable());
      myModuleRootListener.assertNoEvents();

      ModuleRootModificationUtil.setModuleSdk(a, jdk);
      myModuleRootListener.assertEventsCount(1);

      SdkModificator sdkModificator = jdk.getSdkModificator();
      sdkModificator.addRoot(fixture.createTempVirtualDir(), OrderRootType.CLASSES);
      sdkModificator.commitChanges();
    });

    myModuleRootListener.assertEventsCount(1);
  }

  @Test
  public void testModuleJdkEditing() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    WriteAction.runAndWait(() -> {
      final Module moduleA = fixture.createModule("a.iml");
      final Module moduleB = fixture.createModule("b.iml");
      myModuleRootListener.assertEventsCount(2);

      final Sdk jdk = ProjectJdkTable.getInstance().createSdk("new-jdk", JavaSdk.getInstance());
      final Sdk unused = ProjectJdkTable.getInstance().createSdk("unused", JavaSdk.getInstance());
      ProjectJdkTable.getInstance().addJdk(jdk, fixture.getTestRootDisposable());
      ProjectJdkTable.getInstance().addJdk(unused, fixture.getTestRootDisposable());
      myModuleRootListener.assertNoEvents();

      final ModifiableRootModel rootModelA = ModuleRootManager.getInstance(moduleA).getModifiableModel();
      final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
      rootModelA.setSdk(jdk);
      rootModelB.setSdk(jdk);
      ModifiableRootModel[] rootModels = {rootModelA, rootModelB};
      ModifiableModelCommitter.multiCommit(rootModels, ModuleManager.getInstance(rootModels[0].getProject()).getModifiableModel());
      myModuleRootListener.assertEventsCount(1);

      final SdkModificator sdkModificator = jdk.getSdkModificator();
      sdkModificator.addRoot(fixture.createTempVirtualDir(), OrderRootType.CLASSES);
      sdkModificator.commitChanges();

      myModuleRootListener.assertEventsCount(1);

      final SdkModificator sdkModificator2 = unused.getSdkModificator();
      sdkModificator2.addRoot(fixture.createTempVirtualDir(), OrderRootType.CLASSES);
      sdkModificator2.commitChanges();
      myModuleRootListener.assertNoEvents();
    });
  }

  @Test
  public void testSetupUnknownJdk() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    WriteAction.runAndWait(() -> {
      Module module = fixture.createModule("a.iml");
      myModuleRootListener.assertEventsCount(1);
      ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
      model.setInvalidSdk("new-jdk", JavaSdk.getInstance().getName());
      model.commit();
      myModuleRootListener.assertEventsCount(1);

      Sdk unusedJdk = ProjectJdkTable.getInstance().createSdk("unused-jdk", JavaSdk.getInstance());
      ProjectJdkTable.getInstance().addJdk(unusedJdk, fixture.getTestRootDisposable());
      myModuleRootListener.assertNoEvents();
      
      Sdk jdk = ProjectJdkTable.getInstance().createSdk("new-jdk", JavaSdk.getInstance());
      ProjectJdkTable.getInstance().addJdk(jdk, fixture.getTestRootDisposable());

      myModuleRootListener.assertEventsCount(1);

      final SdkModificator sdkModificator = jdk.getSdkModificator();
      sdkModificator.addRoot(fixture.createTempVirtualDir(), OrderRootType.CLASSES);
      sdkModificator.commitChanges();
      myModuleRootListener.assertEventsCount(1);
    });
  }

  @Test
  public void testInheritedJdkEditing() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    WriteAction.runAndWait(() -> {
      final Module moduleA = fixture.createModule("a.iml");
      final Module moduleB = fixture.createModule("b.iml");
      myModuleRootListener.assertEventsCount(2);

      final Sdk jdk;
      final Sdk jdkBBB;
      jdk = ProjectJdkTable.getInstance().createSdk("AAA", JavaSdk.getInstance());
      ProjectJdkTable.getInstance().addJdk(jdk, fixture.getTestRootDisposable());
      myModuleRootListener.assertNoEvents();

      jdkBBB = ProjectJdkTable.getInstance().createSdk("BBB", JavaSdk.getInstance());
      ProjectJdkTable.getInstance().addJdk(jdk, fixture.getTestRootDisposable());
      myModuleRootListener.assertNoEvents();

      ProjectRootManager.getInstance(fixture.getProject()).setProjectSdk(jdkBBB);

      // don't care if there are references from modules to project SDK or not: change in the "project sdk" always generates events
      myModuleRootListener.assertEventsCount(1);

      final ModifiableRootModel rootModelA = ModuleRootManager.getInstance(moduleA).getModifiableModel();
      final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
      rootModelA.inheritSdk();
      rootModelB.inheritSdk();
      ModifiableRootModel[] rootModels = {rootModelA, rootModelB};
      ModifiableModelCommitter.multiCommit(rootModels, ModuleManager.getInstance(rootModels[0].getProject()).getModifiableModel());
      myModuleRootListener.assertEventsCount(1);

      ProjectRootManager.getInstance(fixture.getProject()).setProjectSdk(jdk);
      myModuleRootListener.assertEventsCount(1);

      final SdkModificator sdkModificator = jdk.getSdkModificator();
      sdkModificator.addRoot(fixture.createTempVirtualDir(), OrderRootType.CLASSES);
      sdkModificator.commitChanges();
      myModuleRootListener.assertEventsCount(1);
    });
  }

  private void verifyLibraryTableEditing(final LibraryTable libraryTable) {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    final Module moduleA = fixture.createModule("a.iml");
    final Module moduleB = fixture.createModule("b.iml");
    myModuleRootListener.assertEventsCount(2);

    WriteAction.runAndWait(() -> {
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
      VirtualFile file = fixture.createTempVirtualDir();
      libraryModifiableModel2.addRoot(file.getUrl(), OrderRootType.CLASSES);
      libraryModifiableModel2.commit();
      myModuleRootListener.assertEventsCount(1);

      libraryTable.removeLibrary(libraryA);
      myModuleRootListener.assertEventsCount(1);

      final Library libraryQ = libraryTable.createLibrary("Q");
      myModuleRootListener.assertEventsCount(1);

      Library.ModifiableModel model = libraryQ.getModifiableModel();
      model.addRoot(fixture.createTempVirtualDir(), OrderRootType.CLASSES);
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
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    WriteAction.runAndWait(() -> {
      final Module moduleA = fixture.createModule("a.iml");
      final Module moduleB = fixture.createModule("b.iml");
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

  @Test
  public void testShelveChangesMustNotLeadToRootsChangedEvent() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    // create .idea
    StoreUtil.saveSettings(fixture.getProject());
    VirtualFile shelf = createChildDirectory(fixture.getProject().getProjectFile().getParent(), "shelf");
    VcsIgnoreManager vcsIgnoreManager = VcsIgnoreManager.getInstance(fixture.getProject());

    myModuleRootListener.reset();

    VirtualFile xxx = createChildData(shelf, "shelf1.dat");
    assertTrue(vcsIgnoreManager.isPotentiallyIgnoredFile(xxx));

    assertEquals(myModuleRootListener.getModificationCount(), ProjectRootManager.getInstance(fixture.getProject()).getModificationCount());

    VirtualFile newShelf = createChildDirectory(HeavyTestHelper.getOrCreateProjectBaseDir(fixture.getProject()).getParent(), "newShelf");
    VcsConfiguration vcs = VcsConfiguration.getInstance(fixture.getProject());
    vcs.USE_CUSTOM_SHELF_PATH = true;
    vcs.CUSTOM_SHELF_PATH = newShelf.getPath();
    myModuleRootListener.reset();

    VirtualFile xxx2 = createChildData(newShelf, "shelf1.dat");
    assertTrue(vcsIgnoreManager.isPotentiallyIgnoredFile(xxx2));
  }

  @Test
  public void testCreationDeletionOfRootDirectoriesMustLeadToRootsChanged() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    File root = heavyFixture.get().createTempVirtualDir().toNioPath().toFile();

    File dir1 = new File(root, "dir1");
    assertTrue(dir1.mkdirs());
    VirtualFile contentRoot = StandardFileSystems.local().refreshAndFindFileByPath(dir1.getAbsolutePath());
    assertNotNull(contentRoot);

    Module moduleA = fixture.createModule("a.iml");
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

      CompilerModuleExtension moduleExtension = model.getModuleExtension(CompilerModuleExtension.class);
      moduleExtension.inheritCompilerOutputPath(false);
      moduleExtension.setCompilerOutputPath(outputUrl);
      moduleExtension.setCompilerOutputPathForTests(testOutputUrl);
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
    EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());

    VirtualFile dir = createChildDirectory(contentRoot, new File(dirUrl).getName());
    myModuleRootListener.assertEventsCount(mustGenerateEvents);

    myModuleRootListener.reset();
    EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue());
    VfsTestUtil.deleteFile(dir);
    myModuleRootListener.assertEventsCount(mustGenerateEvents);
  }

  @Test
  public void testEmptyDirectoryCreatedAndSomeRogueFileListenerImmediatelyCallsGetChildrenPreventingFurtherCreationEventsForFilesThatHappenedToBeAlreadyThereByThatMoment() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    File ioRoot = heavyFixture.get().createTempVirtualDir().toNioPath().toFile();

    VirtualFile vRoot = StandardFileSystems.local().refreshAndFindFileByPath(ioRoot.getAbsolutePath());
    vRoot.getChildren();

    Module module = fixture.createModule("a.iml");
    ModuleRootModificationUtil.addContentRoot(module, vRoot.getPath());
    ModuleRootModificationUtil.updateModel(module, model -> model.getContentEntries()[0].addExcludeFolder(vRoot.getUrl() + "/parent/excluded"));

    MessageBusConnection connect = ApplicationManager.getApplication().getMessageBus().connect(fixture.getTestRootDisposable());
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

    File iParent = new File(ioRoot, "parent");
    assertTrue(iParent.mkdirs());
    File ioExcluded = new File(iParent, "excluded");
    EdtTestUtil.runInEdtAndWait(() -> {
      vRoot.refresh(true, true);

      TimeoutUtil.sleep(1000); // hope that now async refresh has found "parent" and is waiting for EDT to fire events

      assertTrue(ioExcluded.mkdirs());
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue(); // now events are fired
    });

    assertNotNull(StandardFileSystems.local().refreshAndFindFileByPath(ioExcluded.getAbsolutePath()));
  }

  @Test
  public void testChangesInsideCompilerOutputDirectoryMustNotLeadToRootsChange() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    File temp = heavyFixture.get().createTempVirtualDir().toNioPath().toFile();
    VirtualFile root = StandardFileSystems.local().refreshAndFindFileByPath(temp.getAbsolutePath());
    VirtualFile content = VfsTestUtil.createDir(root, "content");

    Module module = fixture.createModule("a.iml");
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

  @Test
  public void testBulkRootsChanging() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    WriteAction.runAndWait(() -> {
      myModuleRootListener.reset();
      ProjectRootManagerEx.getInstanceEx(fixture.getProject()).mergeRootsChangesDuring(() -> {
        for (int i = 0; i < 10; i++) {
          ModifiableRootModel model = ModuleRootManager.getInstance(fixture.getModule()).getModifiableModel();
          File dir = fixture.createTempVirtualDir().toNioPath().toFile();
          VirtualFile vDir = StandardFileSystems.local().refreshAndFindFileByPath(dir.getAbsolutePath());
          model.addContentEntry(vDir);
          model.commit();

          assertEquals(1, myModuleRootListener.getBeforeCount());
          assertEquals(0, myModuleRootListener.getAfterCount());
        }
      });
      myModuleRootListener.assertEventsCount(1);
    });
  }

  @Test
  public void testRootDirDeletionDoesntLeadToIndexing() throws IOException {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    File contentRoot = fixture.createTempVirtualDir().toNioPath().toFile();
    File excludedRoot = new File(contentRoot, "excluded-root");
    assertTrue(excludedRoot.mkdirs());
    VirtualFile excludedRootVFile = StandardFileSystems.local().refreshAndFindFileByPath(excludedRoot.getAbsolutePath());

    ModuleRootModificationUtil.updateModel(fixture.getModule(), model -> model.addContentEntry(excludedRootVFile.getParent()).addExcludeFolder(excludedRootVFile));

    AtomicInteger dumbModeCount = new AtomicInteger();
    SimpleMessageBusConnection connection = fixture.getProject().getMessageBus().simpleConnect();
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

  @Test
  public void testRootsChangeEventAtCreatingAndMoveFolderUnderJarDir() throws IOException {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    String rootJarDirName = "jarDir";

    File temp = heavyFixture.get().createTempVirtualDir().toNioPath().toFile();
    VirtualFile root = StandardFileSystems.local().refreshAndFindFileByPath(temp.getAbsolutePath());
    VirtualFile jarDir = VfsTestUtil.createDir(root, rootJarDirName);

    LibraryTable projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(fixture.getProject());
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

  @Test
  public void testRootsChangeEventAtRenameCopyAndDeleteFileUnderJarDir() throws IOException {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    String rootJarDirName = "jarDir";

    File temp = heavyFixture.get().createTempVirtualDir().toNioPath().toFile();
    VirtualFile root = StandardFileSystems.local().refreshAndFindFileByPath(temp.getAbsolutePath());
    VirtualFile testFileUnderRoot = VfsTestUtil.createFile(root, "test.jar");

    VirtualFile jarDir = VfsTestUtil.createDir(root, rootJarDirName);
    VirtualFile testFileUnderJarDir = VfsTestUtil.createFile(jarDir, "test.jar");

    LibraryTable projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(fixture.getProject());
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
