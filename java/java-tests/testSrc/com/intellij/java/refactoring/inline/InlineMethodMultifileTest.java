// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.java.testFramework.fixtures.MultiModuleProject;
import com.intellij.java.testFramework.fixtures.MultiModuleProjectDescriptor;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.inline.InlineMethodProcessor;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.intellij.java.testFramework.fixtures.JavaModulesFixturesKt.javaModulesCodeInsightFixture;
import static com.intellij.java.testFramework.fixtures.JavaModulesFixturesKt.multiModuleProjectFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class InlineMethodMultifileTest {
  private static final String TEST_DATA_PATH = JavaTestUtil.getJavaTestDataPath() + "/refactoring/inlineMethod/multifile/";

  private final TestFixture<MultiModuleProject> projectFixture =
    multiModuleProjectFixture(() -> new MultiModuleProjectDescriptor(Paths.get(TEST_DATA_PATH), "main", null));
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture = javaModulesCodeInsightFixture(projectFixture, TEST_DATA_PATH);
  private String testName;

  @BeforeEach
  void setUpTestName(TestInfo testInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), true);
  }

  @Test
  public void testRemoveStaticImports() throws IOException {
    doTest("Foo", "foo");
  }

  @Test
  public void testPreserveStaticImportsIfOverloaded() throws IOException {
    doTest("Foo", "foo");
  }

  @Test
  public void testDecodeQualifierInMethodReference() throws IOException {
    doTest("Foo", "foo");
  }

  @Test
  public void testRemoveStaticImportsIfOverloadedUnused() throws IOException {
    doTest("Foo", "foo");
  }

  @Test
  public void testMultiModuleDependencies() throws IOException {
    doTest("Foo", "foo", "src/module-info.java");
  }

  private void doTest(String className, String methodName, String... additionalFiles) throws IOException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    MultiModuleProject project = projectFixture.get();
    MultiModuleProjectDescriptor myDescriptor = project.getDescriptor();
    String packageName = "org.jetbrains." + testName;
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiClass aClass = myFixture.findClass(packageName + "." + className);
      assertNotNull(aClass);
      PsiMethod method = aClass.findMethodsByName(methodName, false)[0];
      final boolean condition = InlineMethodProcessor.checkBadReturns(method) && !InlineUtil.allUsagesAreTailCalls(method);
      assertFalse(condition, "Bad returns found");

      new InlineMethodProcessor(myFixture.getProject(), method, null, myFixture.getEditor(), false).run();

      Path expectedPath = myDescriptor.getAfterPath().resolve("src/org/jetbrains/" + testName);
      VirtualFile rootAfter = VirtualFileManager.getInstance().findFileByNioPath(expectedPath);
      assertNotNull(rootAfter);

      Path actualPath = project.getModule().getModuleNioFile().getParent().resolve("src/org/jetbrains/" + testName);
      VirtualFile actualDirectory = VirtualFileManager.getInstance().findFileByNioPath(actualPath);
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
        VirtualFile expected = VirtualFileManager.getInstance().findFileByNioPath(expectedFilePath);
        Path actualFilePath = project.getModule().getModuleNioFile().getParent().resolve(file);
        VirtualFile actual = VirtualFileManager.getInstance().findFileByNioPath(actualFilePath);

        actual.putUserData(VfsTestUtil.TEST_DATA_FILE_PATH, expectedFilePath.toString());
        PlatformTestUtil.assertFilesEqual(expected, actual);
      }
    });
  }
}
