// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.java19api;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.java19api.JavaEmptyModuleInfoFileInspection;
import com.intellij.java.JavaBundle;
import com.intellij.java.testFramework.fixtures.MultiModuleProject;
import com.intellij.java.testFramework.fixtures.MultiModuleProjectDescriptor;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
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
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class JavaEmptyModuleInfoFileInspectionTest {
  private static final String TEST_DATA_PATH = JavaTestUtil.getJavaTestDataPath() + "/inspection/emptyModuleInfoFile";

  private final TestFixture<MultiModuleProject> projectFixture = multiModuleProjectFixture(name -> {
    MultiModuleProjectDescriptor value =
      new MultiModuleProjectDescriptor(Paths.get(TEST_DATA_PATH + "/" + PlatformTestUtil.getTestName(name, true)), "main", null);
    Path lib = value.getProjectPath().getParent().getParent().resolve("lib");
    Path beforeLib = value.getBeforePath().getParent().getParent().resolve("lib");
    try {
      FileUtilRt.deleteRecursively(lib);
      FileUtil.copyDir(beforeLib.toFile(), lib.toFile());
    }
    catch (IOException ignore) {
      fail("Failed to copy lib files");
    }
    return value;
  });
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture = javaModulesCodeInsightFixture(projectFixture, TEST_DATA_PATH);
  private String testName;

  @BeforeEach
  void setUpTestName(TestInfo testInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), true);
  }

  @Test
  public void testSingleLibraryDependency() {
    doTest("src/module-info.java");
    doTest("test/module-info.java");
  }

  @Test
  public void testSingleModule() {
    doTest("src/module-info.java");
    doTest("test/module-info.java");
  }

  @Test
  public void testInvalidLibraryName() {
    doTest("test/module-info.java");
  }

  @Test
  public void testMultiModuleProject() {
    doTest("src/module-info.java", "main/");
  }

  private void doTest(@NotNull String path) {
    doTest(path, "");
  }

  private void doTest(@NotNull String path, @NotNull String dir) {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile file = projectFixture.get().getModule().getModuleFile().getParent().findFileByRelativePath(path);
    myFixture.configureFromExistingVirtualFile(file);

    JavaEmptyModuleInfoFileInspection inspection = new JavaEmptyModuleInfoFileInspection();
    myFixture.enableInspections(inspection);
    EdtTestUtil.runInEdtAndWait(() -> myFixture.testHighlighting(true, false, false, file));

    EdtTestUtil.runInEdtAndWait(() -> {
      IntentionAction intention = myFixture.getAvailableIntention(JavaBundle.message("inspection.auto.add.module.requirements.quickfix"));
      if (intention != null) {
        myFixture.launchAction(intention);
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      }
      myFixture.checkResultByFile(testName + "/after/" + dir + path);
    });
  }
}
