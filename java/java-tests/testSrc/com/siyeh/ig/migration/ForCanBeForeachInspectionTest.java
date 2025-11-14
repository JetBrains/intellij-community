// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.module.Module;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.RunInEdt;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaCodeInsightFixture;
import static com.intellij.testFramework.JavaCodeInsightFixtureKt.setUpJdk;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.*;

@TestApplication
@RunInEdt(allMethods = false)
@TestDataPath("$PROJECT_ROOT/community/java/java-tests/testData/ig/com/siyeh/igtest/migration/foreach")
public class ForCanBeForeachInspectionTest {
  private static final TestFixture<Disposable> disposable = disposableFixture();
  private static final TestFixture<Path> tempDir = tempPathFixture();
  private static final TestFixture<Project> project = projectFixture(tempDir, OpenProjectTask.build(), true);
  private static final TestFixture<Module> module = moduleFixture(project, tempDir, true);

  private final TestFixture<String> testName = testNameFixture(false);
  private final TestFixture<JavaCodeInsightTestFixture> fixture =  javaCodeInsightFixture(project, tempDir);

  @BeforeAll
  static void beforeAll() {
    setUpJdk(LanguageLevel.JDK_1_8, project.get(), module.get(), disposable.get());
  }

  @BeforeEach
  void setUp() {
    fixture.get().enableInspections(new ForCanBeForeachInspection());
  }

  @Test
  void forCanBeForEach() {
    fixture.get().testHighlighting(testName.get() + ".java");
  }
}
