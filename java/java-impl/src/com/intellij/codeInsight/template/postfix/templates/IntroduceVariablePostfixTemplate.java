package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.introduceVariable.InputValidator;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.refactoring.introduceVariable.IntroduceVariableSettings;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import org.jetbrains.annotations.NotNull;

// todo: support for int[].var (parses as .class access!)
public class IntroduceVariablePostfixTemplate extends ExpressionPostfixTemplateWithChooser {
  public IntroduceVariablePostfixTemplate() {
    super("var", "Introduces variable for expression", "T name = expr;");
  }

  @Override
  protected void doIt(@NotNull Editor editor, @NotNull PsiExpression expression) {
    // for advanced stuff use ((PsiJavaCodeReferenceElement)expression).advancedResolve(true).getElement();
    IntroduceVariableHandler handler = ApplicationManager.getApplication().isUnitTestMode() ? getMockHandler() : new IntroduceVariableHandler();
    handler.invoke(expression.getProject(), editor, expression);
  }

  @NotNull
  private static IntroduceVariableHandler getMockHandler() {
    return new IntroduceVariableHandler() {
      // mock default settings
      @Override
      public final IntroduceVariableSettings getSettings(Project project, Editor editor, final PsiExpression expr,
                                                         PsiExpression[] occurrences, TypeSelectorManagerImpl typeSelectorManager,
                                                         boolean declareFinalIfAll, boolean anyAssignmentLHS, InputValidator validator,
                                                         PsiElement anchor, OccurrencesChooser.ReplaceChoice replaceChoice) {
        return new IntroduceVariableSettings() {
          @Override
          public String getEnteredName() {
            return "foo";
          }

          @Override
          public boolean isReplaceAllOccurrences() {
            return false;
          }

          @Override
          public boolean isDeclareFinal() {
            return false;
          }

          @Override
          public boolean isReplaceLValues() {
            return false;
          }

          @Override
          public PsiType getSelectedType() {
            return expr.getType();
          }

          @Override
          public boolean isOK() {
            return true;
          }
        };
      }
    };
  }
}