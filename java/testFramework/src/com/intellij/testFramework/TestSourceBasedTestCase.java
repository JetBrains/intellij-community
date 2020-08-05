// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

@SuppressWarnings({"HardCodedStringLiteral", "ConstantConditions", "JUnitTestCaseInProductSource"})
public abstract class TestSourceBasedTestCase extends JavaProjectTestCase {
  private File myTempDirectory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTempDirectory = createTempDirectoryWithSuffix("test").toFile();
    String testPath = getTestPath();
    if (testPath != null) {
      final File testRoot = new File(getTestDataPath(), testPath);
      assertTrue(testRoot.getAbsolutePath(), testRoot.isDirectory());

      final File currentTestRoot = new File(testRoot, getTestDirectoryName());
      assertTrue(currentTestRoot.getAbsolutePath(), currentTestRoot.isDirectory());

      FileUtil.copyDir(currentTestRoot, new File(myTempDirectory, getTestDirectoryName()));

      ApplicationManager.getApplication().runWriteAction(this::setupContentRoot);
    }

    ProjectViewTestUtil.setupImpl(getProject(), true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      FileEditorManagerEx.getInstanceEx(getProject()).closeAllFiles();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath(getClass());
  }

  @Nullable
  protected abstract String getTestPath();

  private File getTestContentFile() {
    return new File(myTempDirectory, getTestDirectoryName());
  }

  private void setupContentRoot() {
    PsiTestUtil.addContentRoot(myModule, getContentRoot());
    VirtualFile src = getContentRoot().findChild("src");
    if (src != null) {
      PsiTestUtil.addSourceRoot(myModule, src);
    }
  }

  protected VirtualFile getContentRoot() {
    File file = getTestContentFile();
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
  }

  @NotNull
  @Override
  protected String getTestDirectoryName() {
    return getTestName(true);
  }


  protected PsiDirectory getPackageDirectory(final String packageRelativePath) {
    return getPsiManager().findDirectory(getContentRoot().findFileByRelativePath("src/" + packageRelativePath));
  }

  protected PsiDirectory getSrcDirectory() {
    return getPsiManager().findDirectory(getContentRoot().findFileByRelativePath("src"));
  }

  protected PsiDirectory getContentDirectory() {
    return getPsiManager().findDirectory(getContentRoot());
  }

  protected String getRootFiles() {
    return " " + PathUtil.getFileName(myModule.getModuleFilePath()) + "\n";
  }
}
