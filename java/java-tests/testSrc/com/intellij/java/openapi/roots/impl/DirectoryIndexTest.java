/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.openapi.roots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.DirectoryIndexTestCase;
import com.intellij.openapi.roots.impl.DirectoryInfo;
import com.intellij.openapi.roots.impl.ModuleFileIndexImpl;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.util.*;

@PlatformTestCase.WrapInCommand
public class DirectoryIndexTest extends DirectoryIndexTestCase {

  private Module myModule2, myModule3;
  private VirtualFile myRootVFile;
  private VirtualFile myModule1Dir, myModule2Dir, myModule3Dir;
  private VirtualFile mySrcDir1, mySrcDir2;
  private VirtualFile myTestSrc1;
  private VirtualFile myPack1Dir, myPack2Dir;
  private VirtualFile myFileLibDir, myFileLibSrc, myFileLibCls;
  private VirtualFile myLibAdditionalOutsideDir, myLibAdditionalOutsideSrcDir, myLibAdditionalOutsideExcludedDir;
  private VirtualFile myLibDir, myLibSrcDir, myLibAdditionalDir, myLibAdditionalSrcDir, myLibAdditionalSrcFile, myLibAdditionalExcludedDir, myLibClsDir;
  private VirtualFile myCvsDir;
  private VirtualFile myExcludeDir;
  private VirtualFile myOutputDir;
  private VirtualFile myModule1OutputDir;
  private VirtualFile myResDir, myTestResDir;
  private VirtualFile myExcludedLibSrcDir, myExcludedLibClsDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final File root = createTempDirectory();

    ApplicationManager.getApplication().runWriteAction(() -> {
      /*
        root
            lib
                file.src
                file.cls
            additional-lib
                src
                excluded
            module1
                src1
                    pack1
                    testSrc
                        pack2
                res
                testRes
                lib
                    src
                      exc
                    cls
                      exc
                additional-lib
                    src
                    a.txt
                    excluded
                module2
                    src2
                        CVS
                        excluded
            module3
            out
                module1
      */
      myRootVFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(root);
      assertNotNull(myRootVFile);

      myFileLibDir = createChildDirectory(myRootVFile, "lib");
      myFileLibSrc = createChildData(myFileLibDir, "file.src");
      myFileLibCls = createChildData(myFileLibDir, "file.cls");
      myLibAdditionalOutsideDir = createChildDirectory(myRootVFile, "additional-lib");
      myLibAdditionalOutsideSrcDir = createChildDirectory(myLibAdditionalOutsideDir, "src");
      myLibAdditionalOutsideExcludedDir = createChildDirectory(myLibAdditionalOutsideDir, "excluded");
      myModule1Dir = createChildDirectory(myRootVFile, "module1");
      mySrcDir1 = createChildDirectory(myModule1Dir, "src1");
      myPack1Dir = createChildDirectory(mySrcDir1, "pack1");
      myTestSrc1 = createChildDirectory(mySrcDir1, "testSrc");
      myPack2Dir = createChildDirectory(myTestSrc1, "pack2");
      myResDir = createChildDirectory(myModule1Dir, "res");
      myTestResDir = createChildDirectory(myModule1Dir, "testRes");

      myLibDir = createChildDirectory(myModule1Dir, "lib");
      myLibSrcDir = createChildDirectory(myLibDir, "src");
      myExcludedLibSrcDir = createChildDirectory(myLibSrcDir, "exc");
      myLibAdditionalDir = createChildDirectory(myModule1Dir, "additional-lib");
      myLibAdditionalSrcDir = createChildDirectory(myLibAdditionalDir, "src");
      myLibAdditionalSrcFile = createChildData(myLibAdditionalDir, "a.txt");
      myLibAdditionalExcludedDir = createChildDirectory(myLibAdditionalDir, "excluded");
      myLibClsDir = createChildDirectory(myLibDir, "cls");
      myExcludedLibClsDir = createChildDirectory(myLibClsDir, "exc");
      myModule2Dir = createChildDirectory(myModule1Dir, "module2");
      mySrcDir2 = createChildDirectory(myModule2Dir, "src2");
      myCvsDir = createChildDirectory(mySrcDir2, "CVS");
      myExcludeDir = createChildDirectory(mySrcDir2, "excluded");

      myModule3Dir = createChildDirectory(myRootVFile, "module3");

      myOutputDir = createChildDirectory(myRootVFile, "out");
      myModule1OutputDir = createChildDirectory(myOutputDir, "module1");

      getCompilerProjectExtension().setCompilerOutputUrl(myOutputDir.getUrl());
      ModuleManager moduleManager = ModuleManager.getInstance(myProject);

      // fill roots of module1
      {
        ModuleRootModificationUtil.setModuleSdk(myModule, null);
        PsiTestUtil.addContentRoot(myModule, myModule1Dir);
        PsiTestUtil.addSourceRoot(myModule, mySrcDir1);
        PsiTestUtil.addSourceRoot(myModule, myTestSrc1, true);
        PsiTestUtil.addSourceRoot(myModule, myResDir, JavaResourceRootType.RESOURCE);
        PsiTestUtil.addSourceRoot(myModule, myTestResDir, JavaResourceRootType.TEST_RESOURCE);

        ModuleRootModificationUtil.addModuleLibrary(myModule, "lib.js",
                                                    Collections.singletonList(myFileLibCls.getUrl()), Collections
                                                      .singletonList(myFileLibSrc.getUrl()));
        PsiTestUtil.addExcludedRoot(myModule, myExcludedLibClsDir);
        PsiTestUtil.addExcludedRoot(myModule, myExcludedLibSrcDir);
      }

      // fill roots of module2
      {
        VirtualFile moduleFile = createChildData(myModule2Dir, "module2.iml");
        myModule2 = moduleManager.newModule(moduleFile.getPath(), StdModuleTypes.JAVA.getId());

        PsiTestUtil.addContentRoot(myModule2, myModule2Dir);
        PsiTestUtil.addSourceRoot(myModule2, mySrcDir2);
        PsiTestUtil.addExcludedRoot(myModule2, myExcludeDir);
        ModuleRootModificationUtil.addModuleLibrary(myModule2, "lib",
                                                    Collections.singletonList(myLibClsDir.getUrl()), Collections.singletonList(myLibSrcDir.getUrl()),
                                                    Arrays.asList(myExcludedLibClsDir.getUrl(), myExcludedLibSrcDir.getUrl()), DependencyScope.COMPILE, true);
      }
      PlatformTestUtil.registerExtension(AdditionalLibraryRootsProvider.EP_NAME, new AdditionalLibraryRootsProvider() {
        @NotNull
        @Override
        public Collection<SyntheticLibrary> getAdditionalProjectLibraries(@NotNull Project project) {
          return myProject == project ? Collections.singletonList(
            SyntheticLibrary.newImmutableLibrary(ContainerUtil.newArrayList(myLibAdditionalDir, myLibAdditionalOutsideDir),
                                                 ContainerUtil.newHashSet(myLibAdditionalExcludedDir, myLibAdditionalOutsideExcludedDir), null)
          ) : Collections.emptyList();
        }
      }, getTestRootDisposable());

      // fill roots of module3
      {
        VirtualFile moduleFile = createChildData(myModule3Dir, "module3.iml");
        myModule3 = moduleManager.newModule(moduleFile.getPath(), StdModuleTypes.JAVA.getId());

        PsiTestUtil.addContentRoot(myModule3, myModule3Dir);
        ModuleRootModificationUtil.addDependency(myModule3, myModule2);
      }
    });

    // to not interfere with previous test firing vfs events
    VirtualFileManager.getInstance().syncRefresh();
  }

  @Override
  protected void tearDown() throws Exception {
    myModule2 = null;
    myModule3 = null;
    super.tearDown();
  }

  private CompilerProjectExtension getCompilerProjectExtension() {
    final CompilerProjectExtension instance = CompilerProjectExtension.getInstance(myProject);
    assertNotNull(instance);
    return instance;
  }

  public void testDirInfos() {
    assertNotInProject(myRootVFile);

    // beware: files in directory index
    checkInfo(myFileLibSrc, null, false, true, "", null, myModule);
    checkInfo(myFileLibCls, null, true, false, "", null, myModule);

    checkInfo(myLibAdditionalOutsideSrcDir, null, false, true, null, null);
    assertExcludedFromProject(myLibAdditionalOutsideExcludedDir);
    assertIndexableContent(Collections.singletonList(myLibAdditionalOutsideSrcDir),
                           Collections.singletonList(myLibAdditionalOutsideExcludedDir));

    checkInfo(myModule1Dir, myModule, false, false, null, null);
    checkInfo(mySrcDir1, myModule, false, false, "", JavaSourceRootType.SOURCE, myModule);
    checkInfo(myPack1Dir, myModule, false, false, "pack1", JavaSourceRootType.SOURCE, myModule);
    checkInfo(myTestSrc1, myModule, false, false, "", JavaSourceRootType.TEST_SOURCE, myModule);
    checkInfo(myPack2Dir, myModule, false, false, "pack2", JavaSourceRootType.TEST_SOURCE, myModule);
    checkInfo(myResDir, myModule, false, false, "", JavaResourceRootType.RESOURCE, myModule);
    checkInfo(myTestResDir, myModule, false, false, "", JavaResourceRootType.TEST_RESOURCE, myModule);

    checkInfo(myLibDir, myModule, false, false, null, null);
    checkInfo(myLibSrcDir, myModule, false, true, "", null, myModule2, myModule3);
    checkInfo(myLibClsDir, myModule, true, false, "", null, myModule2, myModule3);

    assertEquals(myLibSrcDir, assertInProject(myLibSrcDir).getSourceRoot());

    checkInfo(myModule2Dir, myModule2, false, false, null, null);
    checkInfo(mySrcDir2, myModule2, false, false, "", JavaSourceRootType.SOURCE, myModule2, myModule3);
    assertNotInProject(myCvsDir);
    assertExcluded(myExcludeDir, myModule2);
    assertExcluded(myExcludedLibClsDir, myModule);
    assertExcluded(myExcludedLibSrcDir, myModule);

    assertEquals(myModule1Dir, assertInProject(myLibClsDir).getContentRoot());

    checkInfo(myModule3Dir, myModule3, false, false, null, null);

    VirtualFile cvs = createChildDirectory(myPack1Dir, "CVS");
    assertNotInProject(cvs);
    assertNull(myFileIndex.getPackageNameByDirectory(cvs));
  }

  public void testDirsByPackageName() {
    checkPackage("", true, mySrcDir1, myTestSrc1, myResDir, myTestResDir, mySrcDir2, myLibSrcDir, myLibClsDir);
    checkPackage("", false, mySrcDir1, myTestSrc1, myResDir, myTestResDir, mySrcDir2, myLibClsDir);

    checkPackage("pack1", true, myPack1Dir);
    checkPackage("pack1", false, myPack1Dir);

    checkPackage("pack2", true, myPack2Dir);
    checkPackage("pack2", false, myPack2Dir);

    checkPackage(".pack2", false);
    checkPackage(".pack2", true);

    VirtualFile libClsPack = createChildDirectory(myLibClsDir, "pack1");
    VirtualFile libSrcPack = createChildDirectory(myLibSrcDir, "pack1");
    fireRootsChanged();
    checkPackage("pack1", true, myPack1Dir, libSrcPack, libClsPack);
    checkPackage("pack1", false, myPack1Dir, libClsPack);
  }

  public void testDirectoriesWithPackagePrefix() {
    PsiTestUtil.addSourceRoot(myModule3, myModule3Dir);
    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      final ModifiableRootModel model = ModuleRootManager.getInstance(myModule3).getModifiableModel();
      model.getContentEntries()[0].getSourceFolders()[0].setPackagePrefix("pack1");
      model.commit();
    });
    checkPackage("pack1", true, myPack1Dir, myModule3Dir);
  }

  public void testPackageDirectoriesWithDots() {
    VirtualFile fooBar = createChildDirectory(mySrcDir1, "foo.bar");
    VirtualFile goo1 = createChildDirectory(fooBar, "goo");
    VirtualFile foo = createChildDirectory(mySrcDir2, "foo");
    VirtualFile bar = createChildDirectory(foo, "bar");
    VirtualFile goo2 = createChildDirectory(bar, "goo");

    checkPackage("foo", false, foo);
    checkPackage("foo.bar", false, bar, fooBar);
    checkPackage("foo.bar.goo", false, goo2, goo1);
  }

  public void testCreateDir() {
    String path = mySrcDir1.getPath().replace('/', File.separatorChar);
    assertTrue(new File(path + File.separatorChar + "dir1" + File.separatorChar + "dir2").mkdirs());
    assertTrue(new File(path + File.separatorChar + "CVS").mkdirs());
    VirtualFileManager.getInstance().syncRefresh();
  }

  public void testDeleteDir() {
    VirtualFile subdir1 = createChildDirectory(mySrcDir1, "subdir1");
    VirtualFile subdir2 = createChildDirectory(subdir1, "subdir2");
    createChildDirectory(subdir2, "subdir3");

    VfsTestUtil.deleteFile(subdir1);
  }

  public void testMoveDir() {
    VirtualFile subdir = createChildDirectory(mySrcDir2, "subdir1");
    createChildDirectory(subdir, "subdir2");

    move(subdir, mySrcDir1);
  }

  public void testRenameDir() {
    VirtualFile subdir = createChildDirectory(mySrcDir2, "subdir1");
    createChildDirectory(subdir, "subdir2");

    rename(subdir, "abc.d");
  }

  public void testRenameRoot() {
    rename(myModule1Dir, "newName");
  }

  public void testMoveRoot() {
    move(myModule1Dir, myModule3Dir);
  }

  public void testAddProjectDir() {
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() {
        VirtualFile newDir = createChildDirectory(myModule1Dir.getParent(), "newDir");
        createChildDirectory(newDir, "subdir");

        PsiTestUtil.addContentRoot(myModule, newDir);
      }
    }.execute().throwException();
  }

  public void testChangeIgnoreList() {
    VirtualFile newDir = createChildDirectory(myModule1Dir, "newDir");

    assertInProject(newDir);

    final FileTypeManagerEx fileTypeManager = (FileTypeManagerEx)FileTypeManager.getInstance();
    final String list = fileTypeManager.getIgnoredFilesList();
    final String list1 = list + ";" + "newDir";
    try {
      ApplicationManager.getApplication().runWriteAction(() -> fileTypeManager.setIgnoredFilesList(list1));
      assertNotInProject(newDir);
    }
    finally {
      ApplicationManager.getApplication().runWriteAction(() -> fileTypeManager.setIgnoredFilesList(list));
      assertInProject(newDir);
    }
  }

  public void testIgnoredFile() {
    VirtualFile ignoredFile = createChildData(myModule1Dir, "CVS");
    DirectoryInfo info = myIndex.getInfoForFile(ignoredFile);
    assertTrue(info.isIgnored());
    assertTrue(myFileIndex.isExcluded(ignoredFile));
    assertTrue(myFileIndex.isUnderIgnored(ignoredFile));
    assertNull(myFileIndex.getContentRootForFile(ignoredFile, false));
    assertNull(myFileIndex.getModuleForFile(ignoredFile, false));
  }

  public void testAddModule() {
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() {
        VirtualFile newModuleContent = createChildDirectory(myRootVFile, "newModule");
        createChildDirectory(newModuleContent, "subDir");
        ModuleManager moduleManager = ModuleManager.getInstance(myProject);
        Module module = moduleManager.newModule(myRootVFile.getPath() + "/newModule.iml", StdModuleTypes.JAVA.getId());
        PsiTestUtil.addContentRoot(module, newModuleContent);
      }
    }.execute().throwException();
  }

  public void testModuleUnderIgnoredDir() {
    final VirtualFile ignored = createChildDirectory(myRootVFile, ".git");
    assertTrue(FileTypeManager.getInstance().isFileIgnored(ignored));
    assertTrue(myFileIndex.isExcluded(ignored));
    assertTrue(myFileIndex.isUnderIgnored(ignored));
    final VirtualFile module4 = createChildDirectory(ignored, "module4");
    assertFalse(FileTypeManager.getInstance().isFileIgnored(module4));
    assertTrue(myFileIndex.isExcluded(module4));
    assertTrue(myFileIndex.isUnderIgnored(module4));

    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() {
        ModuleManager moduleManager = ModuleManager.getInstance(myProject);
        Module module = moduleManager.newModule(myRootVFile.getPath() + "/newModule.iml", StdModuleTypes.JAVA.getId());
        PsiTestUtil.addContentRoot(module, module4);
        assertNotInProject(ignored);
        checkInfo(module4, module, false, false, null, null);
      }
    }.execute().throwException();
  }

  public void testModuleInIgnoredDir() {
    final VirtualFile ignored = createChildDirectory(myRootVFile, ".git");
    assertTrue(FileTypeManager.getInstance().isFileIgnored(ignored));

    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() {
        ModuleManager moduleManager = ModuleManager.getInstance(myProject);
        ModifiableModuleModel model = moduleManager.getModifiableModel();
        model.disposeModule(myModule);
        model.disposeModule(myModule2);
        model.disposeModule(myModule3);
        model.commit();
        Module module = moduleManager.newModule(myRootVFile.getPath() + "/newModule.iml", StdModuleTypes.JAVA.getId());
        PsiTestUtil.addContentRoot(module, ignored);
        checkInfo(ignored, module, false, false, null, null);
      }
    }.execute().throwException();
  }

  public void testExcludedDirsInLibraries() {
    assertFalse(myFileIndex.isInLibraryClasses(myExcludedLibClsDir));
    assertTrue(myFileIndex.isExcluded(myExcludedLibClsDir));
    assertFalse(myFileIndex.isUnderIgnored(myExcludedLibClsDir));
    assertFalse(myFileIndex.isInLibrarySource(myExcludedLibSrcDir));
    assertFalse(myFileIndex.isInSource(myExcludedLibSrcDir));
    assertTrue(myFileIndex.isExcluded(myExcludedLibSrcDir));
    assertFalse(myFileIndex.isUnderIgnored(myExcludedLibSrcDir));
  }

  public void testExplicitExcludeOfInner() {
    PsiTestUtil.addExcludedRoot(myModule, myModule2Dir);

    checkInfo(myModule2Dir, myModule2, false, false, null, null);
    checkInfo(mySrcDir2, myModule2, false, false, "", JavaSourceRootType.SOURCE, myModule2, myModule3);
  }

  public void testResettingProjectOutputPath() {
    VirtualFile output1 = createChildDirectory(myModule1Dir, "output1");
    VirtualFile output2 = createChildDirectory(myModule1Dir, "output2");

    assertInProject(output1);
    assertInProject(output2);

    getCompilerProjectExtension().setCompilerOutputUrl(output1.getUrl());
    fireRootsChanged();

    assertExcluded(output1, myModule);
    assertInProject(output2);

    getCompilerProjectExtension().setCompilerOutputUrl(output2.getUrl());
    fireRootsChanged();

    assertInProject(output1);
    assertExcluded(output2, myModule);
  }

  private void fireRootsChanged() {
    ApplicationManager.getApplication().runWriteAction(() -> ProjectRootManagerEx.getInstanceEx(getProject()).makeRootsChange(EmptyRunnable.getInstance(), false, true));
  }

  private static OrderEntry[] toArray(Collection<OrderEntry> orderEntries) {
    return orderEntries.toArray(new OrderEntry[orderEntries.size()]);
  }

  public void testModuleSourceAsLibrarySource() {
    ModuleRootModificationUtil.addModuleLibrary(myModule, "someLib", Collections.emptyList(), Collections.singletonList(mySrcDir1.getUrl()));

    checkInfo(mySrcDir1, myModule, false, true, "", JavaSourceRootType.SOURCE, myModule, myModule);
    Collection<OrderEntry> entriesResult = myIndex.getOrderEntries(myIndex.getInfoForFile(mySrcDir1));
    OrderEntry[] entries = toArray(entriesResult);

    assertInstanceOf(entries[0], LibraryOrderEntry.class);
    assertInstanceOf(entries[1], ModuleSourceOrderEntry.class);

    checkInfo(myTestSrc1, myModule, false, true, "testSrc", JavaSourceRootType.TEST_SOURCE, myModule, myModule);
    entriesResult = myIndex.getOrderEntries(myIndex.getInfoForFile(myTestSrc1));
    entries = toArray(entriesResult);
    assertInstanceOf(entries[0], LibraryOrderEntry.class);
    assertInstanceOf(entries[1], ModuleSourceOrderEntry.class);
  }

  public void testModuleSourceAsLibraryClasses() {
    ModuleRootModificationUtil.addModuleLibrary(myModule, "someLib", Collections.singletonList(mySrcDir1.getUrl()), Collections.emptyList());
    checkInfo(mySrcDir1, myModule, true, false, "", JavaSourceRootType.SOURCE, myModule);
    assertInstanceOf(assertOneElement(toArray(myIndex.getOrderEntries(assertInProject(mySrcDir1)))), ModuleSourceOrderEntry.class);
  }

  public void testModulesWithSameSourceContentRoot() {
    // now our API allows this (ReformatCodeActionTest), although UI doesn't. Maybe API shouldn't allow it as well?
    PsiTestUtil.addContentRoot(myModule2, myModule1Dir);
    PsiTestUtil.addSourceRoot(myModule2, mySrcDir1);

    checkInfo(myModule1Dir, myModule, false, false, null, null);
    checkInfo(mySrcDir1, myModule, false, false, "", JavaSourceRootType.SOURCE, myModule3, myModule);
    checkInfo(myTestSrc1, myModule, false, false, "", JavaSourceRootType.TEST_SOURCE, myModule3, myModule);
    checkInfo(myResDir, myModule, false, false, "", JavaResourceRootType.RESOURCE, myModule);

    checkInfo(mySrcDir2, myModule2, false, false, "", JavaSourceRootType.SOURCE, myModule2, myModule3);
    assertEquals(myModule2Dir, myIndex.getInfoForFile(mySrcDir2).getContentRoot());
  }

  public void testModuleWithSameSourceRoot() {
    PsiTestUtil.addSourceRoot(myModule2, mySrcDir1);
    checkInfo(mySrcDir1, myModule2, false, false, "", JavaSourceRootType.SOURCE, myModule2, myModule3);
    checkInfo(myTestSrc1, myModule2, false, false, "testSrc", JavaSourceRootType.SOURCE, myModule2, myModule3);
  }

  public void testModuleContentUnderSourceRoot() {
    PsiTestUtil.addContentRoot(myModule2, myPack1Dir);
    checkInfo(myPack1Dir, myModule2, false, false, null, null);
  }

  public void testSameSourceAndOutput() {
    PsiTestUtil.setCompilerOutputPath(myModule, mySrcDir1.getUrl(), false);
    assertExcluded(mySrcDir1, myModule);
  }

  public void testExcludedDirShouldBeExcludedRightAfterItsCreation() {
    VirtualFile excluded = createChildDirectory(myModule1Dir, "excluded");
    VirtualFile projectOutput = createChildDirectory(myModule1Dir, "projectOutput");
    VirtualFile module2Output = createChildDirectory(myModule1Dir, "module2Output");
    VirtualFile module2TestOutput = createChildDirectory(myModule2Dir, "module2TestOutput");

    assertInProject(excluded);
    assertInProject(projectOutput);
    assertInProject(module2Output);
    assertInProject(module2TestOutput);

    getCompilerProjectExtension().setCompilerOutputUrl(projectOutput.getUrl());

    PsiTestUtil.addExcludedRoot(myModule, excluded);
    PsiTestUtil.setCompilerOutputPath(myModule2, module2Output.getUrl(), false);
    PsiTestUtil.setCompilerOutputPath(myModule2, module2TestOutput.getUrl(), true);
    PsiTestUtil.setExcludeCompileOutput(myModule2, true);

    assertExcluded(excluded, myModule);
    assertExcluded(projectOutput, myModule);
    assertExcluded(module2Output, myModule);
    assertExcluded(module2TestOutput, myModule2);

    VfsTestUtil.deleteFile(excluded);
    VfsTestUtil.deleteFile(projectOutput);
    VfsTestUtil.deleteFile(module2Output);
    VfsTestUtil.deleteFile(module2TestOutput);

    final List<VirtualFile> created = new ArrayList<>();
    VirtualFileListener l = new VirtualFileListener() {
      @Override
      public void fileCreated(@NotNull VirtualFileEvent e) {
        VirtualFile file = e.getFile();
        String fileName = e.getFileName();
        assertExcluded(file, fileName.contains("module2TestOutput") ? myModule2 : myModule);
        created.add(file);
      }
    };
    VirtualFileManager.getInstance().addVirtualFileListener(l, getTestRootDisposable());

    excluded = createChildDirectory(myModule1Dir, excluded.getName());
    assertExcluded(excluded, myModule);

    projectOutput = createChildDirectory(myModule1Dir, projectOutput.getName());
    assertExcluded(projectOutput, myModule);

    module2Output = createChildDirectory(myModule1Dir, module2Output.getName());
    assertExcluded(module2Output, myModule);

    module2TestOutput = createChildDirectory(myModule2Dir, module2TestOutput.getName());
    assertExcluded(module2TestOutput, myModule2);

    assertEquals(created.toString(), 4, created.size());
  }

  public void testExcludesShouldBeRecognizedRightOnRefresh() {
    final VirtualFile dir = createChildDirectory(myModule1Dir, "dir");
    final VirtualFile excluded = createChildDirectory(dir, "excluded");
    PsiTestUtil.addExcludedRoot(myModule, excluded);
    VfsTestUtil.deleteFile(dir);


    boolean created = new File(myModule1Dir.getPath(), "dir/excluded/foo").mkdirs();
    assertTrue(created);

    VirtualFileListener l = new VirtualFileListener() {
      @Override
      public void fileCreated(@NotNull VirtualFileEvent e) {
        assertEquals("dir", e.getFileName());

        VirtualFile file = e.getFile();
        assertInProject(file);
        assertExcluded(file.findFileByRelativePath("excluded"), myModule);
        assertExcluded(file.findFileByRelativePath("excluded/foo"), myModule);
      }
    };

    VirtualFileManager.getInstance().addVirtualFileListener(l, getTestRootDisposable());
    VirtualFileManager.getInstance().syncRefresh();
  }

  public void testProcessingNestedContentRootsOfExcludedDirsOnCreation() {
    String rootPath = myModule1Dir.getPath();
    final File f = new File(rootPath, "excludedDir/dir/anotherContentRoot");
    ApplicationManager.getApplication().runWriteAction(() -> {
      ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
      rootModel.getContentEntries()[0]
        .addExcludeFolder(VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(f.getParentFile().getParent())));
      rootModel.commit();

      ModuleRootModificationUtil.addContentRoot(myModule2, FileUtil.toSystemIndependentName(f.getPath()));

      assertTrue(f.getPath(), f.exists() || f.mkdirs());
      LocalFileSystem.getInstance().refresh(false);
    });

    assertExcluded(LocalFileSystem.getInstance().findFileByIoFile(f.getParentFile().getParentFile()), myModule);
    assertInProject(LocalFileSystem.getInstance().findFileByIoFile(f));
  }

  public void testSyntheticLibraryInContent() {
    ModuleRootModificationUtil.addContentRoot(myModule, FileUtil.toSystemIndependentName(myModule1Dir.getPath()));
    checkInfo(myLibAdditionalDir, myModule, false, true, null, null);
    checkInfo(myLibAdditionalSrcDir, myModule, false, true, null, null);
    checkInfo(myLibAdditionalExcludedDir, myModule, false, false, null, null);
    assertInProject(myLibAdditionalExcludedDir);
    assertIndexableContent(Arrays.asList(myLibAdditionalSrcDir, myLibAdditionalSrcFile, myLibAdditionalExcludedDir), null);
  }

  public void testLibraryDirInContent() {
    ModuleRootModificationUtil.addModuleLibrary(myModule, myModule1Dir.getUrl());

    checkInfo(myModule1Dir, myModule, true, false, "", null, myModule);
    checkInfo(mySrcDir1, myModule, true, false, "", JavaSourceRootType.SOURCE, myModule);

    checkInfo(myModule2Dir, myModule2, true, false, "module2", null, myModule);
    checkInfo(mySrcDir2, myModule2, true, false, "", JavaSourceRootType.SOURCE, myModule2, myModule3);
    checkInfo(myExcludeDir, null, true, false, "module2.src2.excluded", null, myModule3);

    checkInfo(myLibDir, myModule, true, false, "lib", null, myModule);
    checkInfo(myLibClsDir, myModule, true, false, "", null, myModule2, myModule3);

    //myModule is included into order entries instead of myModule2 because classes root for libraries dominates on source roots
    checkInfo(myLibSrcDir, myModule, true, true, "", null, myModule, myModule3);

    checkInfo(myResDir, myModule, true, false, "", JavaResourceRootType.RESOURCE, myModule);
    assertInstanceOf(assertOneElement(toArray(myIndex.getOrderEntries(assertInProject(myResDir)))), ModuleSourceOrderEntry.class);

    checkInfo(myExcludedLibSrcDir, null, true, false, "lib.src.exc", null, myModule3, myModule);
    checkInfo(myExcludedLibClsDir, null, true, false, "lib.cls.exc", null, myModule3);

    checkPackage("lib.src.exc", true, myExcludedLibSrcDir);
    checkPackage("lib.cls.exc", true, myExcludedLibClsDir);

    checkPackage("lib.src", true);
    checkPackage("lib.cls", true);

    checkPackage("exc", false);
    checkPackage("exc", true);
  }

  public void testExcludeCompilerOutputOutsideOfContentRoot() {
    assertTrue(myFileIndex.isExcluded(myOutputDir));
    assertFalse(myFileIndex.isUnderIgnored(myOutputDir));
    assertTrue(myFileIndex.isExcluded(myModule1OutputDir));
    assertFalse(myFileIndex.isExcluded(myOutputDir.getParent()));
    assertExcludedFromProject(myOutputDir);
    assertExcludedFromProject(myModule1OutputDir);
    String moduleOutputUrl = myModule1OutputDir.getUrl();

    VfsTestUtil.deleteFile(myOutputDir);

    PsiTestUtil.setCompilerOutputPath(myModule, moduleOutputUrl, false);
    myOutputDir = createChildDirectory(myRootVFile, "out");
    myModule1OutputDir = createChildDirectory(myOutputDir, "module1");

    assertExcludedFromProject(myOutputDir);
    assertExcludedFromProject(myModule1OutputDir);
    assertTrue(myFileIndex.isExcluded(myModule1OutputDir));

    PsiTestUtil.setCompilerOutputPath(myModule, moduleOutputUrl, true);
    PsiTestUtil.setCompilerOutputPath(myModule2, moduleOutputUrl, false);
    PsiTestUtil.setCompilerOutputPath(myModule2, moduleOutputUrl, true);
    PsiTestUtil.setCompilerOutputPath(myModule3, moduleOutputUrl, false);
    PsiTestUtil.setCompilerOutputPath(myModule3, moduleOutputUrl, true);

    // now no module inherits project output dir, but it still should be project-excluded
    assertExcludedFromProject(myOutputDir);

    // project output inside module content shouldn't be projectExcludeRoot
    VirtualFile projectOutputUnderContent = createChildDirectory(myModule1Dir, "projectOutputUnderContent");
    getCompilerProjectExtension().setCompilerOutputUrl(projectOutputUnderContent.getUrl());
    fireRootsChanged();

    assertNotExcluded(myOutputDir);
    assertExcluded(projectOutputUnderContent, myModule);

    VfsTestUtil.deleteFile(projectOutputUnderContent);
    projectOutputUnderContent = createChildDirectory(myModule1Dir, "projectOutputUnderContent");
    assertNotExcluded(myOutputDir);
    assertExcluded(projectOutputUnderContent, myModule);
  }

  public void testFileContentAndSourceRoots() {
    VirtualFile fileRoot = createChildData(myRootVFile, "fileRoot.txt");
    VirtualFile fileSourceRoot = createChildData(myRootVFile, "fileSourceRoot.txt");
    VirtualFile fileTestSourceRoot = createChildData(myRootVFile, "fileTestSourceRoot.txt");

    assertNotInProject(fileRoot);
    assertFalse(myFileIndex.isInContent(fileRoot));
    assertIteratedContent(myFileIndex, null, Arrays.asList(fileRoot, fileSourceRoot, fileTestSourceRoot));

    ContentEntry contentEntry = PsiTestUtil.addContentRoot(myModule, fileRoot);
    assertNotNull(contentEntry);
    assertEquals(fileRoot, contentEntry.getFile());
    checkInfo(fileRoot, myModule, false, false, "", null);
    assertTrue(myFileIndex.isInContent(fileRoot));
    assertFalse(myFileIndex.isInSource(fileRoot));

    PsiTestUtil.addContentRoot(myModule, fileSourceRoot);
    PsiTestUtil.addSourceRoot(myModule, fileSourceRoot);
    checkInfo(fileSourceRoot, myModule, false, false, "", JavaSourceRootType.SOURCE, myModule);
    assertTrue(myFileIndex.isInContent(fileSourceRoot));
    assertTrue(myFileIndex.isInSource(fileSourceRoot));

    PsiTestUtil.addContentRoot(myModule, fileTestSourceRoot);
    PsiTestUtil.addSourceRoot(myModule, fileTestSourceRoot, true);
    checkInfo(fileTestSourceRoot, myModule, false, false, "", JavaSourceRootType.TEST_SOURCE, myModule);
    assertTrue(myFileIndex.isInContent(fileTestSourceRoot));
    assertTrue(myFileIndex.isInSource(fileTestSourceRoot));

    assertIteratedContent(myFileIndex, Arrays.asList(fileRoot, fileSourceRoot, fileTestSourceRoot), null);

    // removing file source root
    PsiTestUtil.removeSourceRoot(myModule, fileTestSourceRoot);
    checkInfo(fileTestSourceRoot, myModule, false, false, "", null);
    assertTrue(myFileIndex.isInContent(fileTestSourceRoot));
    assertFalse(myFileIndex.isInSource(fileTestSourceRoot));
    assertIteratedContent(myFileIndex, Arrays.asList(fileRoot, fileSourceRoot, fileTestSourceRoot), null);

    // removing file content root
    PsiTestUtil.removeContentEntry(myModule, contentEntry.getFile());
    assertNotInProject(fileRoot);
    assertFalse(myFileIndex.isInContent(fileRoot));
    assertFalse(myFileIndex.isInSource(fileRoot));
    assertIteratedContent(myFileIndex, Arrays.asList(fileSourceRoot, fileTestSourceRoot), Collections.singletonList(fileRoot));
  }

  public void testFileSourceRootsUnderDirContentRoot() {
    VirtualFile fileSourceRoot = createChildData(myModule1Dir, "fileSourceRoot.txt");
    assertTrue(myFileIndex.isInContent(fileSourceRoot));
    assertFalse(myFileIndex.isInSource(fileSourceRoot));

    PsiTestUtil.addSourceRoot(myModule, fileSourceRoot);
    assertTrue(myFileIndex.isInContent(fileSourceRoot));
    assertTrue(myFileIndex.isInSource(fileSourceRoot));
    checkInfo(fileSourceRoot, myModule, false, false, "", JavaSourceRootType.SOURCE, myModule);

    // removing file source root
    PsiTestUtil.removeSourceRoot(myModule, fileSourceRoot);
    assertTrue(myFileIndex.isInContent(fileSourceRoot));
    assertFalse(myFileIndex.isInSource(fileSourceRoot));
  }

  public void testFileModuleExcludeRootUnderDirectoryRoot() {
    VirtualFile fileExcludeRoot = createChildData(mySrcDir1, "fileExcludeRoot.txt");
    assertTrue(myFileIndex.isInContent(fileExcludeRoot));
    assertTrue(myFileIndex.isInSource(fileExcludeRoot));
    assertIteratedContent(myFileIndex, Collections.singletonList(fileExcludeRoot), null);

    PsiTestUtil.addExcludedRoot(myModule, fileExcludeRoot);
    assertFalse(myFileIndex.isInContent(fileExcludeRoot));
    assertFalse(myFileIndex.isInSource(fileExcludeRoot));
    assertNull(myFileIndex.getContentRootForFile(fileExcludeRoot));
    assertEquals(myModule1Dir, myFileIndex.getContentRootForFile(fileExcludeRoot, false));
    assertNull(myFileIndex.getModuleForFile(fileExcludeRoot));
    assertEquals(myModule, myFileIndex.getModuleForFile(fileExcludeRoot, false));
    assertExcluded(fileExcludeRoot, myModule);
    assertIteratedContent(myFileIndex, null, Collections.singletonList(fileExcludeRoot));

    // removing file exclude root
    PsiTestUtil.removeExcludedRoot(myModule, fileExcludeRoot);
    assertTrue(myFileIndex.isInContent(fileExcludeRoot));
    assertTrue(myFileIndex.isInSource(fileExcludeRoot));
    assertIteratedContent(myFileIndex, Collections.singletonList(fileExcludeRoot), null);
  }

  public void testFileModuleExcludeRootUnderFileRoot() {
    VirtualFile fileRoot = createChildData(myRootVFile, "fileRoot.txt");
    PsiTestUtil.addContentRoot(myModule, fileRoot);
    checkInfo(fileRoot, myModule, false, false, "", null);
    assertTrue(myFileIndex.isInContent(fileRoot));
    assertIteratedContent(myFileIndex, Collections.singletonList(fileRoot), null);

    PsiTestUtil.addExcludedRoot(myModule, fileRoot);
    assertFalse(myFileIndex.isInContent(fileRoot));
    assertExcluded(fileRoot, myModule);
    assertIteratedContent(myFileIndex, null, Collections.singletonList(fileRoot));

    // removing file exclude root
    PsiTestUtil.removeExcludedRoot(myModule, fileRoot);
    checkInfo(fileRoot, myModule, false, false, "", null);
    assertTrue(myFileIndex.isInContent(fileRoot));
    assertIteratedContent(myFileIndex, Collections.singletonList(fileRoot), null);
  }

  public void testFileLibraryInsideFolderLibrary() {
    VirtualFile file = createChildData(myLibSrcDir, "empty.txt");
    ModuleRootModificationUtil.addModuleLibrary(myModule2, "lib2",
                                                Collections.emptyList(), Collections.singletonList(file.getUrl()),
                                                Collections.emptyList(), DependencyScope.COMPILE, true);

    // same for the dir and for the file
    checkInfo(file, myModule, false, true, "", null, myModule2, myModule3);
    checkInfo(myLibSrcDir, myModule, false, true, "", null, myModule2, myModule3);
  }

  public void testFileContentRootsModifications() {
    assertNotInProject(myRootVFile);
    VirtualFile temp = createChildDirectory(myRootVFile, "temp");

    VirtualFile fileSourceRoot = createChildData(myRootVFile, "fileSourceRoot.txt");
    assertNotInProject(fileSourceRoot);

    PsiTestUtil.addContentRoot(myModule, fileSourceRoot);
    PsiTestUtil.addSourceRoot(myModule, fileSourceRoot);
    checkInfo(fileSourceRoot, myModule, false, false, "", JavaSourceRootType.SOURCE, myModule);
    assertTrue(myFileIndex.isInContent(fileSourceRoot));
    assertTrue(myFileIndex.isInSource(fileSourceRoot));

    // delete and recreate
    VfsTestUtil.deleteFile(fileSourceRoot);
    assertNotInProject(fileSourceRoot);
    assertFalse(myFileIndex.isInContent(fileSourceRoot));
    assertFalse(myFileIndex.isInSource(fileSourceRoot));
    fileSourceRoot = createChildData(myRootVFile, "fileSourceRoot.txt");
    checkInfo(fileSourceRoot, myModule, false, false, "", JavaSourceRootType.SOURCE, myModule);
    assertTrue(myFileIndex.isInContent(fileSourceRoot));
    assertTrue(myFileIndex.isInSource(fileSourceRoot));

    // delete and move from another dir 
    VfsTestUtil.deleteFile(fileSourceRoot);
    assertNotInProject(fileSourceRoot);
    assertFalse(myFileIndex.isInContent(fileSourceRoot));
    assertFalse(myFileIndex.isInSource(fileSourceRoot));
    fileSourceRoot = createChildData(temp, "fileSourceRoot.txt");
    assertNotInProject(fileSourceRoot);
    move(fileSourceRoot, myRootVFile);
    checkInfo(fileSourceRoot, myModule, false, false, "", JavaSourceRootType.SOURCE, myModule);
    assertTrue(myFileIndex.isInContent(fileSourceRoot));
    assertTrue(myFileIndex.isInSource(fileSourceRoot));

    // delete and copy from another dir 
    VfsTestUtil.deleteFile(fileSourceRoot);
    assertNotInProject(fileSourceRoot);
    assertFalse(myFileIndex.isInContent(fileSourceRoot));
    assertFalse(myFileIndex.isInSource(fileSourceRoot));
    fileSourceRoot = createChildData(temp, "fileSourceRoot.txt");
    assertNotInProject(fileSourceRoot);
    fileSourceRoot = copy(fileSourceRoot, myRootVFile, "fileSourceRoot.txt");
    checkInfo(fileSourceRoot, myModule, false, false, "", JavaSourceRootType.SOURCE, myModule);
    assertTrue(myFileIndex.isInContent(fileSourceRoot));
    assertTrue(myFileIndex.isInSource(fileSourceRoot));

    // delete and rename from another file
    VfsTestUtil.deleteFile(fileSourceRoot);
    assertNotInProject(fileSourceRoot);
    assertFalse(myFileIndex.isInContent(fileSourceRoot));
    assertFalse(myFileIndex.isInSource(fileSourceRoot));
    fileSourceRoot = createChildData(myRootVFile, "temp_file.txt");
    assertNotInProject(fileSourceRoot);
    rename(fileSourceRoot, "fileSourceRoot.txt");
    checkInfo(fileSourceRoot, myModule, false, false, "", JavaSourceRootType.SOURCE, myModule);
    assertTrue(myFileIndex.isInContent(fileSourceRoot));
    assertTrue(myFileIndex.isInSource(fileSourceRoot));
  }

  public void testSourceContentRootsUnderExcludedRoot() {
    VirtualFile contentRoot = createChildDirectory(myExcludeDir, "content");
    PsiTestUtil.addContentRoot(myModule2, contentRoot);
    checkInfo(contentRoot, myModule2, false, false, null, null, myModule2, myModule3);
    VirtualFile excludedFile = createChildData(myExcludeDir, "excluded.txt");

    VirtualFile sourceRoot = createChildDirectory(myExcludeDir, "src");
    VirtualFile sourceFile = createChildData(sourceRoot, "source.txt");
    PsiTestUtil.addSourceRoot(myModule2, sourceRoot);
    assertEquals(myModule2Dir, assertInProject(sourceRoot).getContentRoot());
    checkInfo(sourceRoot, myModule2, false, false, "", JavaSourceRootType.SOURCE, myModule2, myModule3);

    VirtualFile contentSourceRoot = createChildDirectory(myExcludeDir, "content-src");
    VirtualFile contentSourceFile = createChildData(sourceRoot, "content-source.txt");
    PsiTestUtil.addSourceContentToRoots(myModule2, contentSourceRoot);
    checkInfo(contentSourceRoot, myModule2, false, false, "", JavaSourceRootType.SOURCE, myModule2, myModule3);

    assertIteratedContent(myModule2, Arrays.asList(sourceFile, contentSourceFile, sourceRoot, contentSourceRoot),
                          Arrays.asList(excludedFile, myExcludeDir));
  }

  public void testSourceContentRootsUnderExcludedRootUnderSourceRoot() {
    VirtualFile excluded = createChildDirectory(myModule2Dir, "excluded");
    PsiTestUtil.addExcludedRoot(myModule2, excluded);
    VirtualFile excludedFile = createChildData(excluded, "excluded.txt");

    VirtualFile contentRoot = createChildDirectory(excluded, "content");
    PsiTestUtil.addContentRoot(myModule2, contentRoot);
    checkInfo(contentRoot, myModule2, false, false, null, null);

    VirtualFile sourceRoot = createChildDirectory(excluded, "src");
    PsiTestUtil.addSourceRoot(myModule2, sourceRoot);
    VirtualFile sourceFile = createChildData(sourceRoot, "source.txt");
    assertEquals(myModule2Dir, assertInProject(sourceRoot).getContentRoot());
    checkInfo(sourceRoot, myModule2, false, false, "", JavaSourceRootType.SOURCE, myModule2, myModule3);

    VirtualFile contentSourceRoot = createChildDirectory(excluded, "content-src");
    VirtualFile contentSourceFile = createChildData(contentSourceRoot, "content-source.txt");
    PsiTestUtil.addSourceContentToRoots(myModule2, contentSourceRoot);
    checkInfo(contentSourceRoot, myModule2, false, false, "", JavaSourceRootType.SOURCE, myModule2, myModule3);

    assertIteratedContent(myModule2, Arrays.asList(sourceFile, contentSourceFile, sourceRoot, contentSourceRoot),
                          Arrays.asList(excludedFile, myExcludeDir));
  }

  public void testExcludedSourceRootUnderExcluded() {
    VirtualFile excluded = createChildDirectory(myModule2Dir, "excluded");
    PsiTestUtil.addExcludedRoot(myModule2, excluded);

    VirtualFile src = createChildDirectory(excluded, "src");
    VirtualFile sourceFile = createChildData(src, "src.txt");
    PsiTestUtil.addSourceRoot(myModule2, src);
    PsiTestUtil.addExcludedRoot(myModule2, src);
    assertExcluded(src, myModule2);
    assertIteratedContent(myModule2, null, Collections.singletonList(sourceFile));
  }

  public void testSourceRootFromUnsupportedFileSystem() {
    VirtualFile httpFile = HttpFileSystem.getInstance().findFileByPath("example.com");
    PsiTestUtil.addSourceRoot(myModule, httpFile);
    assertNotInProject(httpFile);
  }

  private void checkInfo(VirtualFile file,
                         @Nullable Module module,
                         boolean isInLibraryClasses,
                         boolean isInLibrarySource,
                         @Nullable String packageName,
                         @Nullable final JpsModuleSourceRootType<?> moduleSourceRootType,
                         Module... modulesOfOrderEntries) {
    DirectoryInfo info = assertInProject(file);
    assertEquals(module, info.getModule());
    if (moduleSourceRootType != null) {
      assertTrue("isInModuleSource", info.isInModuleSource(file));
      assertEquals(moduleSourceRootType, myIndex.getSourceRootType(info));
    }
    else {
      assertFalse("isInModuleSource", info.isInModuleSource(file));
    }
    assertEquals(isInLibraryClasses, info.hasLibraryClassRoot());
    assertEquals(isInLibrarySource, info.isInLibrarySource(file));
    assertEquals(isInLibraryClasses || isInLibrarySource, myFileIndex.isInLibrary(file));

    if (file.isDirectory()) {
      assertEquals(packageName, myFileIndex.getPackageNameByDirectory(file));
    }

    assertEquals(Arrays.toString(toArray(myIndex.getOrderEntries(info))), modulesOfOrderEntries.length, toArray(myIndex.getOrderEntries(info)).length);
    for (Module aModule : modulesOfOrderEntries) {
      OrderEntry found = ModuleFileIndexImpl.findOrderEntryWithOwnerModule(aModule, myIndex.getOrderEntries(info));
      assertNotNull("not found: " + aModule + " in " + Arrays.toString(toArray(myIndex.getOrderEntries(info))), found);
    }
  }

  private void checkPackage(String packageName, boolean includeLibrarySources, VirtualFile... expectedDirs) {
    VirtualFile[] actualDirs = myIndex.getDirectoriesByPackageName(packageName, includeLibrarySources).toArray(VirtualFile.EMPTY_ARRAY);
    assertNotNull(actualDirs);
    assertOrderedEquals(actualDirs, expectedDirs);

    for (VirtualFile dir : expectedDirs) {
      String actualName = myIndex.getPackageName(dir);
      assertEquals("Invalid package name for dir " + dir + ": " + packageName, packageName, actualName);
    }
  }
}