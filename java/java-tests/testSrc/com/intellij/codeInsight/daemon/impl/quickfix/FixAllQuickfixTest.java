// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.ConstantValueInspection;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.visibility.VisibilityInspection;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.InspectionTestUtil;
import com.siyeh.ig.style.UnnecessaryFullyQualifiedNameInspection;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class FixAllQuickfixTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new ConstantValueInspection(),
      new UnnecessaryFullyQualifiedNameInspection(),
      new MyLocalInspectionTool()
    };
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new GlobalInspectionToolWrapper(InspectionTestUtil.instantiateTool(VisibilityInspection.class)));
    enableInspectionTool(new UnusedDeclarationInspection(true));
    new MyTestInjector(getPsiManager()).injectAll(getTestRootDisposable());
  }

  @Override
  @NonNls
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/fixAll";
  }

  @Override
  protected Sdk getProjectJDK() {
    // jdk 1.7 needed because it contains java.sql.Date for FullyQualifiedName test
    return IdeaTestUtil.getMockJdk17();
  }

  private static class MyLocalInspectionTool extends LocalInspectionTool {
    @Override
    @Nls
    @NotNull
    public String getGroupDisplayName() {
      return "MyGroup";
    }

    @Override
    @Nls
    @NotNull
    public String getDisplayName() {
      return "My fake inspection on literal";
    }

    @Override
    @NonNls
    @NotNull
    public String getShortName() {
      return "My";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
      return new JavaElementVisitor() {
        @Override
        public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
          if ("replaced".equals(expression.getValue())) return;
          holder.registerProblem(expression, "Even in injection", new LocalQuickFix() {
            @Nls(capitalization = Nls.Capitalization.Sentence)
            @NotNull
            @Override
            public String getFamilyName() {
              return "Fix over injection";
            }

            @Override
            public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
              PsiElement element = descriptor.getPsiElement();
              if (element instanceof PsiLiteralExpression) {
                element.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText("\"replaced\"", null));
              }
            }
          });
        }
      };
    }
  }
}