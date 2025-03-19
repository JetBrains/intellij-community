// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.java.testFramework.fixtures.MultiModuleProjectDescriptor;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.inline.InlineMethodProcessor;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class InlineMethodMultifileTest extends LightFixtureCompletionTestCase {

  private MultiModuleProjectDescriptor myDescriptor;

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return myDescriptor == null
           ? myDescriptor =
             new MultiModuleProjectDescriptor(Paths.get(getTestDataPath()), "main", null)
           : myDescriptor;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/inlineMethod/multifile/";
  }

  public void testRemoveStaticImports() throws IOException {
    doTest("Foo", "foo");
  }

  public void testPreserveStaticImportsIfOverloaded() throws IOException {
    doTest("Foo", "foo");
  }

  public void testDecodeQualifierInMethodReference() throws IOException {
    doTest("Foo", "foo");
  }

  public void testRemoveStaticImportsIfOverloadedUnused() throws IOException {
    doTest("Foo", "foo");
  }

  public void testMultiModuleDependencies() throws IOException {
    doTest("Foo", "foo", "src/module-info.java");
  }

  private void doTest(String className, String methodName, String... additionalFiles) throws IOException {
    String packageName = "org.jetbrains." + getTestName(true);
    PsiClass aClass = myFixture.findClass(packageName + "." + className);
    assertNotNull(aClass);
    PsiMethod method = aClass.findMethodsByName(methodName, false)[0];
    final boolean condition = InlineMethodProcessor.checkBadReturns(method) && !InlineUtil.allUsagesAreTailCalls(method);
    assertFalse("Bad returns found", condition);

    new InlineMethodProcessor(getProject(), method, null, getEditor(), false).run();

    Path expectedPath = myDescriptor.getAfterPath().resolve("src/org/jetbrains/" + getTestName(true));
    VirtualFile rootAfter = LocalFileSystem.getInstance().findFileByNioFile(expectedPath);
    assertNotNull(rootAfter);

    Path actualPath = getModule().getModuleNioFile().getParent().resolve("src/org/jetbrains/" + getTestName(true));
    VirtualFile actualDirectory = LocalFileSystem.getInstance().findFileByNioFile(actualPath);
    assertNotNull(actualDirectory);

    VfsUtilCore.visitChildrenRecursively(actualDirectory, new VirtualFileVisitor<Void>() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        file.putUserData(VfsTestUtil.TEST_DATA_FILE_PATH, Path.of(expectedPath.toString(),
                                                                  VfsUtilCore.getRelativePath(file, actualDirectory)).toString());
        return super.visitFile(file);
      }
    });
    PlatformTestUtil.assertDirectoriesEqual(rootAfter, actualDirectory);
    for (String file : additionalFiles) {
      Path expectedFilePath = myDescriptor.getAfterPath().resolve(file);
      VirtualFile expected = LocalFileSystem.getInstance().findFileByNioFile(expectedFilePath);
      Path actualFilePath = getModule().getModuleNioFile().getParent().resolve(file);
      VirtualFile actual = LocalFileSystem.getInstance().findFileByNioFile(actualFilePath);

      actual.putUserData(VfsTestUtil.TEST_DATA_FILE_PATH, expectedFilePath.toString());
      PlatformTestUtil.assertFilesEqual(expected, actual);
    }
  }
}
