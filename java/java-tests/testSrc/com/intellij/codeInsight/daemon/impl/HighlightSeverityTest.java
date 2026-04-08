// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIdentifier;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class HighlightSeverityTest extends LightDaemonAnalyzerTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/highlightSeverity";


  public void testErrorLikeUnusedSymbol() {
    enableInspectionTool(new LocalInspectionTool() {
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
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", true, false);
  }
}
