// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.regex.Pattern;

public class InplaceIntroduceParameterTest extends AbstractJavaInplaceIntroduceTest {
  private static final String BASE_PATH = "/refactoring/inplaceIntroduceParameter/";

  public void testReplaceAll() {
    doTestReplaceAll();
  }

  public void testChainMethodCall() {
    doTestReplaceAll();
  }

  public void testReplaceAll1() {
    doTestReplaceAll();
  }

  public void testReplaceAll2() {
    doTestReplaceAll();
  }

  public void testReplaceAll3() {
    doTestReplaceAll();
  }

  public void testReplaceAllMethodCalls() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest(introducer -> {
     introducer.setReplaceAllOccurrences(true);
     type("string");
   });
  }

  private void doTestReplaceAll() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")));
    doTest(introducer -> introducer.setReplaceAllOccurrences(true));
  }
  
  public void testReplaceOneLeaveParamToDelete() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace this occurrence only")));
    doTest(null);
  }
  
  public void testParamNameEqMethodName() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace this occurrence only")));
    doTest(null);
  }

  public void testBlockScope() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace 0 occurrences in 'if-then' block")));
    doTest(null);
  }

  public void testLocalInsideAnonymous() {
    doTest(null);
  }

  public void testNoConflictingVariableDueToReparse() {
    doTest(null);
  }

  public void testLocalInsideAnonymous1() {
    final Consumer<AbstractInplaceIntroducer> pass = introducer -> {
   };
    String name = getTestName(true);
    configureByFile(getBasePath() + name + getExtension());
    final boolean enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
    try {
      TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
      getEditor().getSettings().setVariableInplaceRenameEnabled(true);

      //ensure extract local var
      final MyIntroduceHandler introduceHandler = createIntroduceHandler();
      introduceHandler.invokeImpl(getProject(), getLocalVariableFromEditor(), getEditor());
      final AbstractInplaceIntroducer introducer = introduceHandler.getInplaceIntroducer();
      pass.accept(introducer);
      TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      assert state != null;
      state.gotoEnd(false);
      UIUtil.dispatchAllInvocationEvents();
      checkResultByFile(getBasePath() + name + "_after" + getExtension());
    }
    finally {
      getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
    }
  }

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  @Override
  protected MyIntroduceHandler createIntroduceHandler() {
    return new MyIntroduceParameterHandler();
  }

  public void testEscapePosition() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace this occurrence only")));
    doTestEscape();
  }

  public void testEscapePositionOnLocal() {
    doTestEscape();
  }

  public void testExtractParamOverLocal() {
    doTest(null);
  }

  public void testExtractConflictingParamOverLocal() {
    try {
      doTest(introducer -> type("p"));
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("There is already a parameter <b><code>p</code></b>. It will conflict with the introduced parameter", e.getMessage());
      return;
    }
    fail("Conflict expected");
  }

  private static class MyIntroduceParameterHandler extends IntroduceParameterHandler implements MyIntroduceHandler {

    @Override
    public boolean invokeImpl(Project project, @NotNull PsiExpression selectedExpr, Editor editor) {
      return super.invokeImpl(project, selectedExpr, editor);
    }

    @Override
    public boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor) {
      return super.invokeImpl(project, localVariable, editor);
    }
  }
}