// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;

import java.io.File;

public class MacroManagerTest extends CodeInsightFixtureTestCase {
  private void doTest(String filePath, String str, String expected) throws Macro.ExecutionCancelledException {
    PsiFile file = myFixture.addFileToProject(filePath, "");
    String actual = MacroManager.getInstance().expandMacrosInString(str, false, getContext(file.getVirtualFile()));
    assertEquals(expected, actual);
  }

  public DataContext getContext(VirtualFile file) {
    Project project = myFixture.getProject();
    return SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.VIRTUAL_FILE, file)
      .add(PlatformCoreDataKeys.PROJECT_FILE_DIRECTORY, PlatformTestUtil.getOrCreateProjectBaseDir(project))
      .build();
  }

  public void testFileParentDirMacro() throws Throwable {
    doTest(
      "foo/bar/baz/test.txt",
      "ans: $FileParentDir(bar)$ ",
      "ans: " + myFixture.getTempDirPath() + File.separator + "foo "
    );
    doTest(
      "foo/bar/baz/test2.txt",
      "ans: $FileDirPathFromParent(bar)$ ",
      "ans: baz" + File.separator + " "
    );
    doTest(
      "foo/bar/baz/test3.txt",
      "ans: $FileDirPathFromParent(foo/bar)$ ",
      "ans: baz" + File.separator + " "
    );
    doTest(
      "foo/bar/baz/test4.txt",
      "ans: $FileDirPathFromParent(foo/bar/baz/)$ ",
      "ans:  "
    );
  }

  public void testFileDirPathFromParentMacro_NoParentFound() throws Throwable {
    PsiFile file = myFixture.addFileToProject("foo/bar/baz/test.txt", "");
    String args = "ans: $FileDirPathFromParent(qqq/)$ ";
    String actual = MacroManager.getInstance().expandMacrosInString(args, false, getContext(file.getVirtualFile()));
    String expected = "ans: " + FileUtil.toSystemDependentName(StringUtil.trimEnd(file.getVirtualFile().getParent().getPath(), "/") + "/ ");
    assertEquals(expected, actual);
  }

  public void testContentRootMacro() throws Exception {
    String root = FileUtil.toSystemDependentName(ModuleRootManager.getInstance(myModule).getContentRoots()[0].getPath());
    doTest("foo/bar/baz.txt", "$ContentRoot$", root);
    doTest("foo/bar/baz2.txt", "$ContentRoot$$ContentRoot$", root+root);
    doTest("foo/bar/baz3.txt", "$ContentRoot$$ContentRoot$$ContentRoot$", root+root+root);
    doTest("foo/bar/baz4.txt", "$ContentRoot$test", root+"test");
    doTest("foo/bar/baz5.txt", "test$ContentRoot$$ContentRoot$", "test"+root+root);
    doTest("foo/bar/baz6.txt", "test$ContentRoot$test$ContentRoot$", "test"+root+"test"+root);
    doTest("foo/bar/baz7.txt", "$ContentRoot$$ContentRoot$$ContentRoot$test", root+root+root+"test");
  }
}