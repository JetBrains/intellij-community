// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;

@SuppressWarnings({"ConstantConditions", "JUnitTestCaseInProductSource"})
public abstract class TestSourceBasedTestCase extends JavaProjectTestCase {
  private Path myTempDirectory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    VirtualFile tempDir = getTempDir().createVirtualDir();
    myTempDirectory = tempDir.toNioPath();
    String testPath = getTestPath();
    if (testPath != null) {
      File testRoot = new File(getTestDataPath(), testPath);
      assertTrue(testRoot.getAbsolutePath(), testRoot.isDirectory());

      File currentTestRoot = new File(testRoot, getTestDirectoryName());
      assertTrue(currentTestRoot.getAbsolutePath(), currentTestRoot.isDirectory());

      FileUtil.copyDir(currentTestRoot, myTempDirectory.resolve(getTestDirectoryName()).toFile());
      tempDir.refresh(false, true);

      ApplicationManager.getApplication().runWriteAction(() -> {
        VirtualFile contentRoot = tempDir.findChild(getTestDirectoryName());
        PsiTestUtil.addContentRoot(myModule, contentRoot);
        VirtualFile src = contentRoot.findChild("src");
        if (src != null) {
          PsiTestUtil.addSourceRoot(myModule, src);
        }
      });
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

  protected abstract @Nullable String getTestPath();

  protected final VirtualFile getContentRoot() {
    return LocalFileSystem.getInstance().findFileByNioFile(myTempDirectory.resolve(getTestDirectoryName()));
  }

  @Override
  protected @NotNull String getTestDirectoryName() {
    return getTestName(true);
  }

  protected final PsiDirectory getPackageDirectory(@NotNull String packageRelativePath) {
    return getPsiManager().findDirectory(getContentRoot().findFileByRelativePath("src/" + packageRelativePath));
  }

  protected final PsiDirectory getSrcDirectory() {
    return getPsiManager().findDirectory(getContentRoot().findFileByRelativePath("src"));
  }

  protected final PsiDirectory getContentDirectory() {
    return getPsiManager().findDirectory(getContentRoot());
  }
}
