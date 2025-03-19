// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.java19api;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.java19api.JavaEmptyModuleInfoFileInspection;
import com.intellij.java.JavaBundle;
import com.intellij.java.testFramework.fixtures.MultiModuleProjectDescriptor;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.util.LazyInitializer;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JavaEmptyModuleInfoFileInspectionTest extends LightMultiFileTestCase {
  private final LazyInitializer.LazyValue<MultiModuleProjectDescriptor> myDescriptor = new LazyInitializer.LazyValue<>(() -> {
    MultiModuleProjectDescriptor value =
      new MultiModuleProjectDescriptor(Paths.get(getTestDataPath() + "/" + getTestName(true)), "main", null);
    Path lib = value.getProjectPath().getParent().getParent().resolve("lib");
    Path beforeLib = value.getBeforePath().getParent().getParent().resolve("lib");
    try {
      FileUtilRt.deleteRecursively(lib);
      FileUtil.copyDir(beforeLib.toFile(), lib.toFile());
    }
    catch (IOException ignore) {
      Assert.fail("Failed to copy lib files");
    }
    return value;
  });

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return myDescriptor.get();
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/emptyModuleInfoFile";
  }

  public void testSingleLibraryDependency() {
    doTest("src/module-info.java");
    doTest("test/module-info.java");
  }

  public void testSingleModule() {
    doTest("src/module-info.java");
    doTest("test/module-info.java");
  }

  public void testInvalidLibraryName() {
    doTest("test/module-info.java");
  }

  public void testMultiModuleProject() {
    doTest("src/module-info.java", "main/");
  }

  private void doTest(@NotNull String path) {
    doTest(path, "");
  }

  private void doTest(@NotNull String path, @NotNull String dir) {
    VirtualFile file = getModule().getModuleFile().getParent().findFileByRelativePath(path);
    myFixture.configureFromExistingVirtualFile(file);

    JavaEmptyModuleInfoFileInspection inspection = new JavaEmptyModuleInfoFileInspection();
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, false, file);

    IntentionAction intention = myFixture.getAvailableIntention(JavaBundle.message("inspection.auto.add.module.requirements.quickfix"));
    if (intention != null) {
      myFixture.launchAction(intention);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    }
    myFixture.checkResultByFile(getTestName(true) + "/after/" + dir + path);
  }
}
