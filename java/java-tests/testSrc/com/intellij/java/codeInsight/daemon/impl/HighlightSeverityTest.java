// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIdentifier;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class HighlightSeverityTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/highlightSeverity";


  @Test
  public void testErrorLikeUnusedSymbol() {
    daemonFixture.get().getFixture().enableInspections(new LocalInspectionTool() {
      @NotNull
      @Override
      public String getShortName() {
        return getDisplayName();
      }

      @NotNull
      @Override
      public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
                                            boolean isOnTheFly,
                                            @NotNull LocalInspectionToolSession session) {
        return new JavaElementVisitor() {
          @Override
          public void visitIdentifier(@NotNull PsiIdentifier identifier) {
            if (identifier.getText().equals("k")) {
              holder.registerProblem(identifier, "Variable 'k' is never used");
            }
          }
        };
      }

      @NotNull
      @Override
      public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.ERROR;
      }

      @Nls
      @NotNull
      @Override
      public String getDisplayName() {
        return "x";
      }

      @Nls
      @NotNull
      @Override
      public String getGroupDisplayName() {
        return getDisplayName();
      }
    });
    daemonFixture.get().doTest(BASE_PATH + "/" + daemonFixture.get().getFixture().getTestName(false) + ".java", true, false);
  }
}
