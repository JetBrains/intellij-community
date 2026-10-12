// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.InplaceIntroduceTestUtil;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestApplication
public class InplaceIntroduceParameterTest extends AbstractJavaInplaceIntroduceTest {
  private static final String BASE_PATH = "/refactoring/inplaceIntroduceParameter/";

  @Test
  public void testReplaceAll() {
    doTestReplaceAll();
  }

  @Test
  public void testChainMethodCall() {
    doTestReplaceAll();
  }

  @Test
  public void testReplaceAll1() {
    doTestReplaceAll();
  }

  @Test
  public void testReplaceAll2() {
    doTestReplaceAll();
  }

  @Test
  public void testReplaceAll3() {
    doTestReplaceAll();
  }

  @Test
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
  
  @Test
  public void testReplaceOneLeaveParamToDelete() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace this occurrence only")));
    doTest(null);
  }
  
  @Test
  public void testParamNameEqMethodName() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace this occurrence only")));
    doTest(null);
  }

  @Test
  public void testBlockScope() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace 0 occurrences in 'if-then' block")));
    doTest(null);
  }

  @Test
  public void testLocalInsideAnonymous() {
    doTest(null);
  }

  @Test
  public void testNoConflictingVariableDueToReparse() {
    doTest(null);
  }

  @Test
  public void testLocalInsideAnonymous1() {
    final Consumer<AbstractInplaceIntroducer> pass = introducer -> {
   };
    String name = getTestName(true);
    configureByFile(getBasePath() + name + getExtension());
    InplaceIntroduceTestUtil.withInplaceRename(getFixture(), () -> {
      InplaceIntroduceTestUtil.runInCommand(getProject(), () -> {
        //ensure extract local var
        final MyIntroduceHandler introduceHandler = createIntroduceHandler();
        introduceHandler.invokeImpl(getProject(), getLocalVariableFromEditor(), getEditor());
        final AbstractInplaceIntroducer introducer = introduceHandler.getInplaceIntroducer();
        pass.accept(introducer);
        TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
        assert state != null;
        state.gotoEnd(false);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      });
      checkResultByFile(getBasePath() + name + "_after" + getExtension());
    });
  }

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  @Override
  protected MyIntroduceHandler createIntroduceHandler() {
    return new MyIntroduceParameterHandler();
  }

  @Test
  public void testEscapePosition() {
    UiInterceptors.register(new ChooserInterceptor(null, Pattern.quote("Replace this occurrence only")));
    doTestEscape();
  }

  @Test
  public void testEscapePositionOnLocal() {
    doTestEscape();
  }

  @Test
  public void testRevertImports(){
    doTestEscape();
  }

  @Test
  public void testExtractParamOverLocal() {
    doTest(null);
  }

  @Test
  public void testExtractConflictingParamOverLocal() {
    assertThatThrownBy(() -> doTest(introducer -> type("p"))).isInstanceOf(BaseRefactoringProcessor.ConflictsInTestsException.class)
      .hasMessageContaining("There is already a parameter <b><code>p</code></b>. It will conflict with the introduced parameter");
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