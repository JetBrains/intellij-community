// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.pom.java.AcceptedLanguageLevelsSettings;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.InplaceIntroduceTestUtil;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import java.util.function.Consumer;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.JavaCodeInsightFixtureKt.setUpJdk;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class AbstractJavaInplaceIntroduceTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  @TestDisposable
  Disposable disposable;

  private String testMethodName;

  @BeforeEach
  void setUpInplaceIntroduceTest(TestInfo testInfo) {
    testMethodName = testInfo.getTestMethod().orElseThrow().getName();
    CodeInsightTestFixture fixture = codeInsightFixture.get();
    fixture.setTestDataPath(JavaTestUtil.getJavaTestDataPath());
    Module module = fixture.getModule();
    if (isJava21()) {
      AcceptedLanguageLevelsSettings.allowLevel(disposable, LanguageLevel.JDK_21_PREVIEW);
      EdtTestUtil.runInEdtAndWait(() -> setUpJdk(LanguageLevel.JDK_21_PREVIEW, getProject(), module, disposable));
      IdeaTestUtil.setModuleLanguageLevel(module, LanguageLevel.JDK_21_PREVIEW, disposable);
      ModuleRootModificationUtil.updateModel(module, DefaultLightProjectDescriptor::addJetBrainsAnnotationsWithTypeUse);
    }
    else {
      EdtTestUtil.runInEdtAndWait(() -> setUpJdk(LanguageLevel.JDK_1_8, getProject(), module, disposable));
    }
  }

  /**
   * Returns {@code true} to set up the project as the {@code JAVA_21} descriptor of {@code LightJavaCodeInsightFixtureTestCase} does.
   * Otherwise, the module gets the mock JDK 1.8.
   */
  protected boolean isJava21() {
    return false;
  }

  protected @NotNull CodeInsightTestFixture getFixture() {
    return codeInsightFixture.get();
  }

  protected @NotNull Project getProject() {
    return projectFixture.get();
  }

  protected @NotNull Module getModule() {
    return getFixture().getModule();
  }

  protected @NotNull String getTestName(boolean lowercaseFirstLetter) {
    return PlatformTestUtil.getTestName(testMethodName, lowercaseFirstLetter);
  }

  protected Editor getEditor() {
    return getFixture().getEditor();
  }

  protected PsiFile getFile() {
    return getFixture().getFile();
  }

  protected void type(@NotNull String s) {
    getFixture().type(s);
  }

  protected void configureByFile(@NotNull String relativePath) {
    getFixture().configureByFile(relativePath);
  }

  protected void checkResultByFile(@NotNull String relativePath) {
    getFixture().checkResultByFile(relativePath);
  }

  /**
   * Switches the fixture from an injected editor to the editor of the host file.
   * Call it on the EDT.
   */
  protected void bringRealEditorBack() {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    if (getEditor() instanceof EditorWindow editorWindow) {
      getFixture().openFileInEditor(FileDocumentManager.getInstance().getFile(editorWindow.getDelegate().getDocument()));
    }
  }

  protected abstract String getBasePath();

  protected String getExtension() {
    return ".java";
  }

  @Nullable
  protected PsiExpression getExpressionFromEditor() {
    final PsiExpression expression = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiExpression.class);
    if (expression instanceof PsiReferenceExpression && expression.getParent() instanceof PsiMethodCallExpression) {
      return (PsiExpression)expression.getParent();
    }
    return expression;
  }

  protected PsiLocalVariable getLocalVariableFromEditor() {
    final PsiLocalVariable localVariable = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()),
                                                                       PsiLocalVariable.class);
    assertNotNull(localVariable);
    return localVariable;
  }

  protected abstract MyIntroduceHandler createIntroduceHandler();

  protected AbstractInplaceIntroducer invokeRefactoring() {
    final MyIntroduceHandler introduceHandler = createIntroduceHandler();
    return invokeRefactoring(introduceHandler);
  }

  protected AbstractInplaceIntroducer invokeRefactoring(MyIntroduceHandler introduceHandler) {
    final PsiExpression expression = getExpressionFromEditor();
    if (expression != null) {
      introduceHandler.invokeImpl(getProject(), expression, getEditor());
    } else {
      final PsiLocalVariable localVariable = getLocalVariableFromEditor();
      introduceHandler.invokeImpl(getProject(), localVariable, getEditor());
    }
    return introduceHandler.getInplaceIntroducer();
  }

  protected void doTest(@Nullable Consumer<? super AbstractInplaceIntroducer> pass) {
    InplaceIntroduceTestUtil.doTest(getFixture(), getBasePath(), getExtension(), testMethodName, this::invokeRefactoring, pass);
  }

  protected void doTestEscape() {
    InplaceIntroduceTestUtil.doTestEscape(getFixture(), getBasePath(), getExtension(), testMethodName, this::invokeRefactoring, null);
  }

  protected void doTestInsideInjection(Consumer<? super AbstractInplaceIntroducer> pass) {
    MyTestInjector testInjector = new MyTestInjector(PsiManager.getInstance(getProject()));
    testInjector.injectAll(getFixture().getTestRootDisposable());
    doTest(pass);
  }

  @SuppressWarnings("UnusedReturnValue")
  public interface MyIntroduceHandler {
    boolean invokeImpl(Project project, @NotNull PsiExpression selectedExpr, Editor editor);
    boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor);
    AbstractInplaceIntroducer getInplaceIntroducer();
  }
}