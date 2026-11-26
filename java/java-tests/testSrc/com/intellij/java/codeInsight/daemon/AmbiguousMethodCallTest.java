/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInspection.compiler.JavacQuirksInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

/**
 * This class is for "lightweight" tests only, i.e. those which can run inside default light project set up
 * For "heavyweight" tests use AdvHighlightingTest
 */
@TestApplication
public class AmbiguousMethodCallTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_7));
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture, () -> List.of(
    new UncheckedWarningLocalInspection(),
    new JavacQuirksInspection(),
    new RedundantCastInspection()
  ));

  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/ambiguousCalls";

  @BeforeEach
  void setUp() {
    daemonFixture.get().getFixture().enableInspections(new UnusedDeclarationInspection());
  }

  private void doTest(boolean checkWarnings, boolean checkInfos) {
    daemonFixture.get().doTest(BASE_PATH + "/" + daemonFixture.get().getFixture().getTestName(false) + ".java", checkWarnings, checkInfos);
  }

  @Test
  public void testBoxingAndStaticMethods() {
    doTest(false, false);
  }

  @Test
  public void testMultipleInheritance() {
    doTest(false, false);
  }
}
