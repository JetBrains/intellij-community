// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.localCanBeFinal.LocalCanBeFinal;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SuppressLocalInspectionTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_3;
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new LocalCanBeFinal(), new FieldCanBeLocalInspection(), new MyLocalInspectionTool()};
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/suppressLocalInspection";
  }


  static class DummyUnavailableQuickFix extends IntentionAndQuickFixAction {
    @Override
    public boolean isAvailable(@NotNull Project project, @Nullable Editor editor, PsiFile psiFile) {
      return false;
    }

    @Override
    public @IntentionName @NotNull String getName() {
      return getFamilyName();
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return "Dummy fix";
    }

    @Override
    public void applyFix(@NotNull Project project, PsiFile psiFile, @Nullable Editor editor) {
    }
  }

  private static class MyLocalInspectionTool extends AbstractBaseJavaLocalInspectionTool {
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
      return new JavaElementVisitor() {
        @Override
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
          if (MethodCallUtils.isCallToMethod(expression, CommonClassNames.JAVA_LANG_STRING, null, "format", (PsiType[]) null)) {
            holder.registerProblem(expression, "I am a dummy problem with unavailable fix", new DummyUnavailableQuickFix());
          }
        }
      };
    }
  }
}

