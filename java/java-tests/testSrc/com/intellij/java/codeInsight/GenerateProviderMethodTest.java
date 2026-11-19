// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.generation.GenerateProviderMethodHandler;
import com.intellij.java.testFramework.fixtures.MultiModuleProject;
import com.intellij.java.testFramework.fixtures.MultiModuleProjectDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static com.intellij.java.testFramework.fixtures.JavaModulesFixturesKt.javaModulesCodeInsightFixture;
import static com.intellij.java.testFramework.fixtures.JavaModulesFixturesKt.multiModuleProjectFixture;

@NeedsIndex.SmartMode(reason = "Provider method completion is not supported in the dumb mode")
@TestApplication
public class GenerateProviderMethodTest {
  private static final String TEST_DATA_PATH = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/generateProviderMethod";

  private final TestFixture<MultiModuleProject> projectFixture =
    multiModuleProjectFixture(() -> new MultiModuleProjectDescriptor(Paths.get(TEST_DATA_PATH), "providers", null));
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture = javaModulesCodeInsightFixture(projectFixture, TEST_DATA_PATH);

  @Test
  public void testNonModuleClass() {
    doTest("src/org/jetbrains/providers/SimpleClass.java");
  }

  @Test
  public void testSimpleProvider() {
    doTest("src/org/jetbrains/providers/MyProviderImpl.java");
  }

  @Test
  public void testWithProvider() {
    doTest("src/org/jetbrains/providers/WithProvider.java");
  }

  @Test
  public void testSubClass() {
    doTest("src/org/jetbrains/providers/MySuperClass.java");
  }

  @Test
  public void testWrongPlace() {
    doTest("src/org/jetbrains/providers/WrongPlace.java");
  }

  private void doTest(@NotNull String path) {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile file = projectFixture.get().getModule().getModuleFile().getParent().findFileByRelativePath(path);
    myFixture.configureFromExistingVirtualFile(file);

    EdtTestUtil.runInEdtAndWait(() -> {
      new GenerateProviderMethodHandler().invoke(myFixture.getProject(), myFixture.getEditor(),
                                                 PsiManager.getInstance(myFixture.getProject()).findFile(file));

      myFixture.checkResultByFile("after/" + path);
    });
  }
}
