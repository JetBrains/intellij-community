// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.ConstantValueInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.visibility.VisibilityInspection;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.InspectionTestUtil;
import com.intellij.testFramework.InspectionsKt;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.style.UnnecessaryFullyQualifiedNameInspection;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class FixAllQuickfixTest {
  private static final String TEST_DATA_PATH = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/fixAll";
  // jdk 1.7 needed because it contains java.sql.Date for FullyQualifiedName test
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.HIGHEST, IdeaTestUtil::getMockJdk17));
  private final TestFixture<LightQuickFixFixture> quickFixFixture = lightQuickFixFixture(projectFixture, TEST_DATA_PATH);

  static List<String> files() {
    return lightQuickFixTestFiles(TEST_DATA_PATH);
  }

  @BeforeEach
  void setUpInspections() {
    JavaCodeInsightTestFixture fixture = quickFixFixture.get().getFixture();
    fixture.enableInspections(new ConstantValueInspection(),
                              new UnnecessaryFullyQualifiedNameInspection(),
                              new MyLocalInspectionTool());
    InspectionsKt.enableInspectionTool(fixture.getProject(),
                                       new GlobalInspectionToolWrapper(InspectionTestUtil.instantiateTool(VisibilityInspection.class)),
                                       fixture.getTestRootDisposable());
    fixture.enableInspections(new UnusedDeclarationInspection(true));
    new MyTestInjector(PsiManager.getInstance(fixture.getProject())).injectAll(fixture.getTestRootDisposable());
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

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  void parameterized(String fileName) {
    quickFixFixture.get().doTest(fileName);
  }
}