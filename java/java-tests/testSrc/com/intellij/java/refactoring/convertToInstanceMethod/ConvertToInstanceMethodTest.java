// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.convertToInstanceMethod;

import com.intellij.JavaTestUtil;
import com.intellij.java.refactoring.LightRefactoringTestCase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.convertToInstanceMethod.ConvertToInstanceMethodDialog;
import com.intellij.refactoring.convertToInstanceMethod.ConvertToInstanceMethodHandler;
import com.intellij.ui.UiInterceptors;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;

public class ConvertToInstanceMethodTest extends LightRefactoringTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testSimple() { doTest(0); }
  public void testInterface() { doTest(0); }
  public void testInterfacePrivate() { doTest(0); }
  public void testInterface2() { doTest(0); }
  public void testInterface3() { doTest(0); }
  public void testTypeParameter() { doTest(0); }
  public void testInterfaceTypeParameter() { doTest(0); }
  public void testJavadocParameter() { doTest(0); }
  public void testConflictingParameterName() { doTest(0); }

  public void testVisibilityConflict() {
    try {
      doTest(0, PsiModifier.PRIVATE);
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Method <b><code>foo(Bar)</code></b> is private and will not be accessible from instance initializer of class " +
                   "<b><code>Test</code></b>.", e.getMessage());
    }
  }

  protected void doTest(int targetParameter) {
    doTest(targetParameter, VisibilityUtil.ESCALATE_VISIBILITY);
  }

  protected void doTest(int targetParameter, String visibility, String... options) {
    final String filePath = getBasePath() + getTestName(false) + ".java";
    configureByFile(filePath);
    UiInterceptors.register(new ConvertToInstanceMethodDialogUiInterceptor(targetParameter, visibility, options));
    new ConvertToInstanceMethodHandler().invoke(getProject(), getEditor(), getFile(), getCurrentEditorDataContext());
    checkResultByFile(filePath + ".after");
  }
  
  protected void doTestException() {
    configureByFile(getBasePath() + getTestName(false) + ".java");
    new ConvertToInstanceMethodHandler().invoke(getProject(), getEditor(), getFile(), getCurrentEditorDataContext());
  }
  
  protected String getBasePath() {
    return "/refactoring/convertToInstanceMethod/";
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_6;
  }

  private static class ConvertToInstanceMethodDialogUiInterceptor extends UiInterceptors.UiInterceptor<ConvertToInstanceMethodDialog> {
    private final int myTargetParameter;
    private final String myVisibility;
    private final String[] myOptions;

    ConvertToInstanceMethodDialogUiInterceptor(int targetParameter, @Nullable String visibility, String... options) {
      super(ConvertToInstanceMethodDialog.class);
      myTargetParameter = targetParameter;
      myVisibility = visibility;
      myOptions = options;
    }

    @Override
    protected void doIntercept(@NotNull ConvertToInstanceMethodDialog dialog) {
      @SuppressWarnings("unchecked") JList<Object> list = (JList<Object>)dialog.getPreferredFocusedComponent();
      ListModel<Object> model = list.getModel();
      int size = model.getSize();
      if (myTargetParameter < 0 || myTargetParameter >= size) {
        fail("targetParameter out of bounds: " + myTargetParameter);
      }
      if (myOptions.length > 0) {
        assertEquals(Arrays.toString(myOptions), toString(model));
      }
      list.setSelectedIndex(myTargetParameter);
      dialog.setVisibility(myVisibility == null ? VisibilityUtil.ESCALATE_VISIBILITY : myVisibility);
      dialog.performOKAction();
    }

    private static String toString(ListModel<Object> model) {
      int size = model.getSize();
      String[] result = new String[size];
      for (int i = 0; i < size; i++) {
        Object o = model.getElementAt(i);
        result[i] = (o instanceof PsiElement e) ? e.getText() : o.toString();
      }
      return Arrays.toString(result);
    }
  }
}
