// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.localCanBeFinal.LocalCanBeFinal;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class SuppressLocalInspectionTest {
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

  private static final String TEST_DATA_PATH =
    JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/suppressLocalInspection";
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_3));
  private final TestFixture<LightQuickFixFixture> quickFixFixture = lightQuickFixFixture(
    projectFixture, TEST_DATA_PATH, () -> List.of(new LocalCanBeFinal(), new FieldCanBeLocalInspection(), new MyLocalInspectionTool()));

  static List<String> files() {
    return lightQuickFixTestFiles(TEST_DATA_PATH);
  }

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  void parameterized(String fileName) {
    quickFixFixture.get().doTest(fileName);
  }
}
