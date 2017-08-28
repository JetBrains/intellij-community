/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.roots;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.ModuleTestCase;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author dsl
 */
public class RootsChangedTest extends ModuleTestCase {
  private MyModuleRootListener myModuleRootListener;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    MessageBusConnection connection = myProject.getMessageBus().connect(getTestRootDisposable());
    myModuleRootListener = new MyModuleRootListener();
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
    assertEventsCount(0);
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
      assertEventsCount(0);

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
      assertEventsCount(0);

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
        assertEventsCount(0);

        jdkBBB = (Sdk)IdeaTestUtil.getMockJdk17("BBB").clone();
      }
      catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
      ProjectJdkTable.getInstance().addJdk(jdk, getTestRootDisposable());
      assertEventsCount(0);

      ProjectRootManager.getInstance(myProject).setProjectSdk(jdkBBB);
      assertEventsCount(0);

      final ModifiableRootModel rootModelA = ModuleRootManager.getInstance(moduleA).getModifiableModel();
      final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
      rootModelA.inheritSdk();
      rootModelB.inheritSdk();
      ModifiableRootModel[] rootModels = {rootModelA, rootModelB};
      if (rootModels.length > 0) {
        ModifiableModelCommitter.multiCommit(rootModels, ModuleManager.getInstance(rootModels[0].getProject()).getModifiableModel());
      }
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
      assertEventsCount(0);

      final ModifiableRootModel rootModelA = ModuleRootManager.getInstance(moduleA).getModifiableModel();
      final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
      rootModelA.addLibraryEntry(libraryA);
      rootModelB.addLibraryEntry(libraryA);
      rootModelA.addInvalidLibrary("Q", libraryTable.getTableLevel());
      rootModelB.addInvalidLibrary("Q", libraryTable.getTableLevel());
      ModifiableRootModel[] rootModels = new ModifiableRootModel[]{rootModelA, rootModelB};
      if (rootModels.length > 0) {
        ModifiableModelCommitter.multiCommit(rootModels, ModuleManager.getInstance(rootModels[0].getProject()).getModifiableModel());
      }
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
      assertEventsCount(0);

      final ModifiableRootModel rootModelA = ModuleRootManager.getInstance(moduleA).getModifiableModel();
      final ModifiableRootModel rootModelB = ModuleRootManager.getInstance(moduleB).getModifiableModel();
      rootModelA.addLibraryEntry(libraryA);
      rootModelB.addLibraryEntry(libraryA);
      final Library.ModifiableModel libraryModifiableModel2 = libraryA.getModifiableModel();
      libraryModifiableModel2.addRoot("file:///b", OrderRootType.CLASSES);
      libraryModifiableModel2.commit();
      assertEventsCount(0);

      libraryTable.removeLibrary(libraryA);
      assertEventsCount(0);

      rootModelA.addInvalidLibrary("Q", libraryTable.getTableLevel());
      rootModelB.addInvalidLibrary("Q", libraryTable.getTableLevel());
      assertEventsCount(0);

      final Library libraryQ = libraryTable.createLibrary("Q");
      assertEventsCount(0);

      ModifiableRootModel[] rootModels = new ModifiableRootModel[]{rootModelA, rootModelB};
      if (rootModels.length > 0) {
        ModifiableModelCommitter.multiCommit(rootModels, ModuleManager.getInstance(rootModels[0].getProject()).getModifiableModel());
      }
      assertEventsCount(1);

      libraryTable.removeLibrary(libraryQ);
      assertEventsCount(1);
    });
  }

  private void assertEventsCount(int count) {
    final int beforeCount = myModuleRootListener.beforeCount;
    final int afterCount = myModuleRootListener.afterCount;
    assertEquals("beforeCount = " + beforeCount + ", afterCount = " + afterCount, beforeCount, afterCount);
    assertEquals(count, beforeCount);
    myModuleRootListener.reset();
  }

  private static class MyModuleRootListener implements ModuleRootListener {
    private int beforeCount = 0;
    private int afterCount = 0;

    @Override
    public void beforeRootsChange(ModuleRootEvent event) {
      beforeCount++;
    }

    @Override
    public void rootsChanged(ModuleRootEvent event) {
      afterCount++;
    }

    private void reset() {
      beforeCount = 0;
      afterCount = 0;
    }
  }
}
