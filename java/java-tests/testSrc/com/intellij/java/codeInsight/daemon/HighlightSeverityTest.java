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
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/highlightSeverity";


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
          public void visitIdentifier(PsiIdentifier identifier) {
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
