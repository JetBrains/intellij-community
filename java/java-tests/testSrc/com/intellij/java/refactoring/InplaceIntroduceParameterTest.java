// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class InplaceIntroduceParameterTest extends AbstractJavaInplaceIntroduceTest {
  private static final String BASE_PATH = "/refactoring/inplaceIntroduceParameter/";

  public void testReplaceAll() {
    doTest(introducer -> introducer.setReplaceAllOccurrences(true));
  }

  public void testChainMethodCall() {
    doTest(introducer -> introducer.setReplaceAllOccurrences(true));
  }

  public void testReplaceAll1() {
    doTest(introducer -> introducer.setReplaceAllOccurrences(true));
  }

  public void testReplaceOneLeaveParamToDelete() {
    doTest(null);
  }

  public void testReplaceAllBrokenIdentifier() {
    doTest(introducer -> {
     type("ONE TWO");
     introducer.setReplaceAllOccurrences(true);
   });
  }

  public void testReplaceAll2() {
    doTest(introducer -> introducer.setReplaceAllOccurrences(true));
  }

  public void testReplaceAll3() {
    doTest(introducer -> introducer.setReplaceAllOccurrences(true));
  }

  public void testReplaceAllMethodCalls() {
    doTest(introducer -> {
     introducer.setReplaceAllOccurrences(true);
     type("string");
   });
  }

  public void testParamNameEqMethodName() {
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