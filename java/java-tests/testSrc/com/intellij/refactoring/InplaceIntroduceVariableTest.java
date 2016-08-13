/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.refactoring;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.testFramework.MapDataContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InplaceIntroduceVariableTest extends AbstractJavaInplaceIntroduceTest {

  @Nullable
  @Override
  protected PsiExpression getExpressionFromEditor() {
    final PsiExpression expression = super.getExpressionFromEditor();
    if (expression != null) {
      return expression;
    }
    final PsiExpression expr = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiExpression.class);
    if (expr == null && InjectedLanguageManager.getInstance(getProject()).isInjectedFragment(getFile())) {
      return PsiTreeUtil.getParentOfType(InjectedLanguageUtil.getTopLevelFile(getFile()).findElementAt(InjectedLanguageUtil.getTopLevelEditor(getEditor()).getCaretModel().getOffset()), PsiExpression.class);
    }
    return expr instanceof PsiLiteralExpression ? expr : null;
  }

  public void testFromExpression() throws Exception {
     doTest(new Pass<AbstractInplaceIntroducer>() {
       @Override
       public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
         type("expr");
       }
     });
  }

  public void testConflictingInnerClassName() throws Exception {
    final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    final boolean oldOption = settings.INSERT_INNER_CLASS_IMPORTS;
    try {
      settings.INSERT_INNER_CLASS_IMPORTS = true;
      doTest(new Pass<AbstractInplaceIntroducer>() {
         @Override
         public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
           type("constants");
         }
       });
    }
    finally {
      settings.INSERT_INNER_CLASS_IMPORTS = oldOption;
    }
  }

  public void testInsideInjectedString() throws Exception {
    doTestInsideInjection(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
        type("expr");
      }
    });
  }

  public void testInjectedString() throws Exception {
    doTestInsideInjection(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
        bringRealEditorBack();
        type("expr");
      }
    });
  }

  public void testPlaceInsideLoopAndRename() throws Exception {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
        type("expr");
      }
    });
  }
  
  public void testPlaceInsideLambdaBody() throws Exception {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
        type("expr");
      }
    });
  }
  
  public void testRanges() throws Exception {
     doTest(new Pass<AbstractInplaceIntroducer>() {
       @Override
       public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
         type("expr");
       }
     });
  }

  public void testFromParenthesis() throws Exception {
     doTest(new Pass<AbstractInplaceIntroducer>() {
       @Override
       public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
         type("expr");
       }
     });
  }

  public void testConflictWithField() throws Exception {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer introducer) {
        type("height");
      }
    });
  }

  public void testConflictWithFieldNoCast() throws Exception {
    doTest(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer introducer) {
        type("weights");
      }
    });
  }

  public void testCast() throws Exception {
    doTestTypeChange("Integer");
  }

  public void testCastToObject() throws Exception {
    doTestTypeChange("Object");
  }

  public void testEscapePosition() {
    doTestStopEditing(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer introducer) {
        invokeEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
        invokeEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
      }
    });
  }

  public void testEscapePositionIfTyped() {
    doTestStopEditing(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer introducer) {
        type("fooBar");
        invokeEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
      }
    });
  }

  public void testWritable() throws Exception {
    doTestReplaceChoice(OccurrencesChooser.ReplaceChoice.ALL);
  }
  
  public void testNoWritable() throws Exception {
    doTestReplaceChoice(OccurrencesChooser.ReplaceChoice.NO_WRITE);
  }
  
  public void testAllInsertFinal() throws Exception {
    doTestReplaceChoice(OccurrencesChooser.ReplaceChoice.ALL);
  }
  
  public void testAllIncomplete() throws Exception {
    doTestReplaceChoice(OccurrencesChooser.ReplaceChoice.ALL);
  }

  public void testStopEditing() {
    doTestStopEditing(new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer introducer) {
        invokeEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT);
        invokeEditorAction(IdeActions.ACTION_EDITOR_ENTER);
        invokeEditorAction(IdeActions.ACTION_EDITOR_ENTER);
      }
    });
  }

  private void doTestStopEditing(Pass<AbstractInplaceIntroducer> pass) {
    String name = getTestName(true);
    configureByFile(getBasePath() + name + getExtension());
    final boolean enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
    try {
      TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
      getEditor().getSettings().setVariableInplaceRenameEnabled(true);

      final AbstractInplaceIntroducer introducer = invokeRefactoring();
      pass.pass(introducer);
      checkResultByFile(getBasePath() + name + "_after" + getExtension());
    }
    finally {
      TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      if (state != null) {
        state.gotoEnd(true);
      }
      getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
    }
  }

  private void doTestTypeChange(final String newType) {
    final Pass<AbstractInplaceIntroducer> typeChanger = new Pass<AbstractInplaceIntroducer>() {
      @Override
      public void pass(AbstractInplaceIntroducer inplaceIntroduceFieldPopup) {
        type(newType);
      }
    };
    String name = getTestName(true);
    configureByFile(getBasePath() + name + getExtension());
    final boolean enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
    try {
      TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
      getEditor().getSettings().setVariableInplaceRenameEnabled(true);

      final AbstractInplaceIntroducer introducer = invokeRefactoring();
      TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      assert state != null;
      state.previousTab();
      typeChanger.pass(introducer);
      state.gotoEnd(false);
      checkResultByFile(getBasePath() + name + "_after" + getExtension());
    }
    finally {
      getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
    }
  }

  private void doTestReplaceChoice(OccurrencesChooser.ReplaceChoice choice) {
    doTestReplaceChoice(choice, null);
  }

  private void doTestReplaceChoice(OccurrencesChooser.ReplaceChoice choice, Pass<AbstractInplaceIntroducer> pass) {
    String name = getTestName(true);
    configureByFile(getBasePath() + name + getExtension());
    final boolean enabled = getEditor().getSettings().isVariableInplaceRenameEnabled();
    try {
      TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
      getEditor().getSettings().setVariableInplaceRenameEnabled(true);

      MyIntroduceHandler handler = createIntroduceHandler();
      ((MyIntroduceVariableHandler)handler).setChoice(choice);
      final AbstractInplaceIntroducer introducer = invokeRefactoring(handler);
      if (pass != null) {
        pass.pass(introducer);
      }
      TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      assert state != null;
      state.gotoEnd(false);
      checkResultByFile(getBasePath() + name + "_after" + getExtension());
    }
    finally {
      getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
    }
  }

  private static void invokeEditorAction(String actionId) {
    EditorActionManager.getInstance().getActionHandler(actionId)
      .execute(getEditor(), getEditor().getCaretModel().getCurrentCaret(), new MapDataContext());
  }


  @Override
  protected String getBasePath() {
    return "/refactoring/inplaceIntroduceVariable/";
  }

  @Override
  protected MyIntroduceHandler createIntroduceHandler() {
    return new MyIntroduceVariableHandler();
  }

  public static class MyIntroduceVariableHandler extends IntroduceVariableHandler implements MyIntroduceHandler {
    private OccurrencesChooser.ReplaceChoice myChoice = null;

    public void setChoice(OccurrencesChooser.ReplaceChoice choice) {
      myChoice = choice;
    }

    @Override
    public boolean invokeImpl(Project project, @NotNull PsiExpression selectedExpr, Editor editor) {
      return super.invokeImpl(project, selectedExpr, editor);
    }

    @Override
    public boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor) {
      return super.invokeImpl(project, localVariable, editor);
    }

    @Override
    protected OccurrencesChooser.ReplaceChoice getOccurrencesChoice() {
      return myChoice;
    }

    @Override
    protected boolean isInplaceAvailableInTestMode() {
      return true;
    }
  }
}
