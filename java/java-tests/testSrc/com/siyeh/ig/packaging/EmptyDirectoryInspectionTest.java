// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.packaging;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.EmptyDirectoryInspection;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.InspectionFixtureTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.io.PathKt;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
@TestDataPath("$PROJECT_ROOT/community/java/java-tests/testData/ig/")
public class EmptyDirectoryInspectionTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<Path> testDataFixture = tempPathFixture();

  @Test
  public void testEmptyDirectory() throws IOException {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String path = "com/siyeh/igtest/packaging/empty_directory/";
    Path testDataRoot = testDataFixture.get();
    Path testDataPath = testDataRoot.resolve(path);
    PathKt.copyRecursively(Path.of(JavaTestUtil.getJavaTestDataPath(), "ig", path), testDataPath);
    Files.createDirectories(testDataPath.resolve("src/simple"));
    myFixture.setTestDataPath(testDataRoot.toString());
    InspectionFixtureTestUtil.doTest(myFixture, path, new GlobalInspectionToolWrapper(new EmptyDirectoryInspection()));
  }
}
