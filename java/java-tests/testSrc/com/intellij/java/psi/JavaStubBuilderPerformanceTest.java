// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.impl.source.JavaLightStubBuilder;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.LocalTimeCounter;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_LATEST;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@PerformanceUnitTest
@StressTestApplication
public class JavaStubBuilderPerformanceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(JAVA_LATEST);

  @Test
  public void testPerformance() throws IOException {
    String path = JavaTestUtil.getJavaTestDataPath() + "/psi/stub/StubPerformanceTest.java";
    String text = FileUtil.loadFile(new File(path));
    String fileName = "test.java";
    ReadAction.runBlocking(() -> {
      PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(projectFixture.get().getProject())
        .createFileFromText(fileName, FileTypeManager.getInstance().getFileTypeByFileName(fileName), text, LocalTimeCounter.currentTime(),
                            false, false);
      String message = "Source file size: " + text.length();
      StubBuilder builder = new JavaLightStubBuilder();
      Benchmark.newBenchmark(message, () -> builder.buildStubTree(file)).start();
    });
  }
}