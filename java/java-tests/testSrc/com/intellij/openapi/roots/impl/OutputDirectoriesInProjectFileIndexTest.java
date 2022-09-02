// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.vfs.*;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class OutputDirectoriesInProjectFileIndexTest extends DirectoryIndexTestCase {
  private VirtualFile myRootDir;
  private VirtualFile myOutputDir;
  private VirtualFile myModule1Dir;
  private VirtualFile mySrcDir1;
  private VirtualFile myModule1OutputDir;
  private Module myModule2;
  private VirtualFile myModule2Dir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final File root = createTempDirectory();
    myRootDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(root);
    myOutputDir = createChildDirectory(myRootDir, "out");
    myModule1Dir = createChildDirectory(myRootDir, "module1");
    mySrcDir1 = createChildDirectory(myModule1Dir, "src1");
    myModule1OutputDir = createChildDirectory(myOutputDir, "module1");
    myModule2Dir = createChildDirectory(myModule1Dir, "module2");
    myModule2 = createJavaModuleWithContent(getProject(), "module2", myModule2Dir);

    getCompilerProjectExtension().setCompilerOutputUrl(myOutputDir.getUrl());
    PsiTestUtil.addContentRoot(myModule, myModule1Dir);
    PsiTestUtil.addSourceRoot(myModule, mySrcDir1);
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
    myOutputDir = createChildDirectory(myRootDir, "out");
    myModule1OutputDir = createChildDirectory(myOutputDir, "module1");

    assertExcludedFromProject(myOutputDir);
    assertExcludedFromProject(myModule1OutputDir);
    assertTrue(myFileIndex.isExcluded(myModule1OutputDir));

    PsiTestUtil.setCompilerOutputPath(myModule, moduleOutputUrl, true);
    PsiTestUtil.setCompilerOutputPath(myModule2, moduleOutputUrl, false);
    PsiTestUtil.setCompilerOutputPath(myModule2, moduleOutputUrl, true);

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

  public void testExcludedOutputDirShouldBeExcludedRightAfterItsCreation() {
    VirtualFile projectOutput = createChildDirectory(myModule1Dir, "projectOutput");
    VirtualFile module2Output = createChildDirectory(myModule1Dir, "module2Output");
    VirtualFile module2TestOutput = createChildDirectory(myModule2Dir, "module2TestOutput");

    assertInProject(projectOutput);
    assertInProject(module2Output);
    assertInProject(module2TestOutput);

    getCompilerProjectExtension().setCompilerOutputUrl(projectOutput.getUrl());

    PsiTestUtil.setCompilerOutputPath(myModule2, module2Output.getUrl(), false);
    PsiTestUtil.setCompilerOutputPath(myModule2, module2TestOutput.getUrl(), true);
    PsiTestUtil.setExcludeCompileOutput(myModule2, true);

    assertExcluded(projectOutput, myModule);
    assertExcluded(module2Output, myModule);
    assertExcluded(module2TestOutput, myModule2);

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

    projectOutput = createChildDirectory(myModule1Dir, projectOutput.getName());
    assertExcluded(projectOutput, myModule);

    module2Output = createChildDirectory(myModule1Dir, module2Output.getName());
    assertExcluded(module2Output, myModule);

    module2TestOutput = createChildDirectory(myModule2Dir, module2TestOutput.getName());
    assertExcluded(module2TestOutput, myModule2);

    assertEquals(created.toString(), 3, created.size());
  }

  public void testSameSourceAndOutput() {
    PsiTestUtil.setCompilerOutputPath(myModule, mySrcDir1.getUrl(), false);
    assertExcluded(mySrcDir1, myModule);
  }

  private CompilerProjectExtension getCompilerProjectExtension() {
    final CompilerProjectExtension instance = CompilerProjectExtension.getInstance(myProject);
    assertNotNull(instance);
    return instance;
  }
}
