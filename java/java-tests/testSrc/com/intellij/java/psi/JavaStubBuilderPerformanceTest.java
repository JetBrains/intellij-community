// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.impl.source.JavaLightStubBuilder;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_LATEST;

public class JavaStubBuilderPerformanceTest extends LightIdeaTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST;
  }


  public void testPerformance() throws IOException {
    String path = PathManagerEx.getTestDataPath() + "/psi/stub/StubPerformanceTest.java";
    String text = FileUtil.loadFile(new File(path));
    PsiJavaFile file = (PsiJavaFile)createLightFile("test.java", text);
    String message = "Source file size: " + text.length();
    StubBuilder builder = new JavaLightStubBuilder();
    Benchmark.newBenchmark(message, () -> builder.buildStubTree(file)).start();
  }
}