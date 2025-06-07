// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.propertyBased;

import com.intellij.codeInsight.CodeInsightFrontbackUtil;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.java.refactoring.InplaceIntroduceVariableTest;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceVariable.InputValidator;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.introduceVariable.IntroduceVariableSettings;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.propertyBased.ActionOnFile;
import com.intellij.testFramework.propertyBased.RandomActivityInterceptor;
import com.intellij.ui.UiInterceptors;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.ImperativeCommand;

import java.util.List;

public class IntroduceVariableActionOnFile extends ActionOnFile {
  public IntroduceVariableActionOnFile(@NotNull PsiFile file) {
    super(file);
  }

  @Override
  public void performCommand(@NotNull ImperativeCommand.Environment env) {
    Project project = getProject();
    PsiDocumentManager.getInstance(getProject()).commitDocument(getDocument());
    int offset = env.generateValue(Generator.integers(0, getDocument().getTextLength()).noShrink(), null);
    Editor editor = FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, getVirtualFile(), offset), true);
    assert editor != null;
    PsiElement element = getFile().findElementAt(offset);
    if (element == null) return;
    List<PsiExpression> expressions = PsiTreeUtil.collectParents(element, PsiExpression.class, true, e -> false);
    expressions = ContainerUtil.filter(expressions, ex -> {
      TextRange exRange = ex.getTextRange();
      return CodeInsightFrontbackUtil.findExpressionInRange(getFile(), exRange.getStartOffset(), exRange.getEndOffset()) == ex;
    });
    if (expressions.isEmpty()) return;
    PsiExpression expression = env.generateValue(Generator.sampledFrom(expressions), null);
    if (!textBlockCanBeExtracted(expression)) {
      env.logMessage("Skipping introduceVariable: cannot extract from token → " + expression.getText());
      return;
    }
    TextRange range = expression.getTextRange();
    editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
    if (env.generateValue(Generator.booleans(), null)) {
      introduceVariableInline(env, project, editor, expression);
    }
    else {
      Disposable disposable = Disposer.newDisposable();
      try {
        UiInterceptors.register(new RandomActivityInterceptor(env, disposable));
        introduceVariableNoInline(env, project, editor, expression);
      }
      finally {
         Disposer.dispose(disposable);
      }
    }
  }

  /**
   * Checks whether it is possible to introduce a variable from the given expression.
   * e.g.
   * <pre><code>
   * class Bar {
   *   void foo() {
   *     """<select>
   *     text
   *   }
   * }</select>
   * </code></pre>
   *
   * @param expr the PSI expression to test
   * @return {@code true} if it’s safe to perform an Introduce Variable refactoring, {@code false} otherwise
   */
  private static boolean textBlockCanBeExtracted(@NotNull PsiExpression expr) {
    if (expr instanceof PsiLiteralExpression literal && literal.isTextBlock()) {
      String text = literal.getText();
      if (text.startsWith("\"\"\"") && !text.endsWith("\"\"\"")) {
        return false;
      }
    }
    return true;
  }

  private static void introduceVariableInline(Environment env, Project project, Editor editor, PsiExpression expression) {
    var handler = new InplaceIntroduceVariableTest.MyIntroduceVariableHandler();
    Disposable disposable = Disposer.newDisposable();
    env.logMessage(String.format("Introduce variable using inline introducer; expression: %s at %d",
                                 expression.getText(), expression.getTextRange().getStartOffset()));
    try {
      UiInterceptors.register(new RandomActivityInterceptor(env, disposable));
      TemplateManagerImpl.setTemplateTesting(disposable);
      handler.invokeImpl(project, expression, editor);
      TemplateState state = TemplateManagerImpl.getTemplateState(editor);
      if (state != null) {
        // Could be null if template wasn't started (e.g. too many occurrences)
        state.gotoEnd(false);
      }
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException hint) {
      env.logMessage("Introduce variable failed gracefully with: " + hint.getMessage());
    }
    finally {
      Disposer.dispose(disposable);
    }
  }


  private static void introduceVariableNoInline(@NotNull Environment env, Project project, Editor editor, PsiExpression expression) {
    String varName;
    do {
      varName = env.generateValue(Generator.asciiIdentifiers(), null);
    }
    while (!PsiNameHelper.getInstance(project).isIdentifier(varName));
    boolean replaceAll = env.generateValue(Generator.booleans(), null);
    boolean declareFinal = env.generateValue(Generator.booleans(), null);
    boolean declareVar = env.generateValue(Generator.booleans(), null);
    boolean replaceLValues = env.generateValue(Generator.booleans(), null);
    String flags = EntryStream.of("replaceAll", replaceAll,
                                  "declareFinal", declareFinal,
                                  "declareVar", declareVar,
                                  "replaceLValues", replaceLValues)
      .filterValues(x -> x).keys().joining(" | ");
    env.logMessage(String.format("Introduce variable; flags: %s; expression: %s at %d",
                                 flags, expression.getText(), expression.getTextRange().getStartOffset()));
    IntroduceVariableBase handler = new MockIntroduceVariableHandler(varName, replaceAll, declareFinal, declareVar, replaceLValues);
    try {
      handler.invoke(project, expression, editor);
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException hint) {
      env.logMessage("Introduce variable failed gracefully with: " + hint.getMessage());
    }
  }

  static class MockIntroduceVariableHandler extends IntroduceVariableBase {
    private final String myName;
    private final boolean myReplaceAll;
    private final boolean myDeclareFinal;
    private final boolean myDeclareVar;
    private final boolean myReplaceLValues;

    MockIntroduceVariableHandler(String name,
                                 boolean replaceAll,
                                 boolean declareFinal,
                                 boolean declareVar,
                                 boolean replaceLValues) {
      myName = name;
      myReplaceAll = replaceAll;
      myDeclareFinal = declareFinal;
      myDeclareVar = declareVar;
      myReplaceLValues = replaceLValues;
    }

    @Override
    public IntroduceVariableSettings getSettings(Project project,
                                                 Editor editor,
                                                 PsiExpression expr,
                                                 PsiExpression[] occurrences,
                                                 TypeSelectorManagerImpl typeSelectorManager,
                                                 boolean declareFinalIfAll,
                                                 boolean anyAssignmentLHS,
                                                 InputValidator validator,
                                                 PsiElement anchor,
                                                 JavaReplaceChoice replaceChoice) {
      boolean isDeclareVarType = myDeclareVar && canBeExtractedWithoutExplicitType(expr);
      return new IntroduceVariableSettings() {
        @Override
        public @NlsSafe String getEnteredName() {
          return myName;
        }

        @Override
        public boolean isReplaceAllOccurrences() {
          return myReplaceAll && occurrences.length > 1;
        }

        @Override
        public boolean isDeclareFinal() {
          return myDeclareFinal || isReplaceAllOccurrences() && declareFinalIfAll;
        }

        @Override
        public boolean isReplaceLValues() {
          return myReplaceLValues;
        }

        @Override
        public PsiType getSelectedType() {
          return typeSelectorManager.getDefaultType();
        }

        @Override
        public boolean isOK() {
          return true;
        }

        @Override
        public boolean isDeclareVarType() {
          return isDeclareVarType;
        }
      };
    }

    @Override
    protected void showErrorMessage(Project project, Editor editor, String message) {
    }
  }
}
