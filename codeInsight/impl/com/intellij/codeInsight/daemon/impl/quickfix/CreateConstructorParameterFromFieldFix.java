package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.AssignFieldFromParameterAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class CreateConstructorParameterFromFieldFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateConstructorParameterFromFieldFix");

  private final PsiField myField;

  public CreateConstructorParameterFromFieldFix(@NotNull PsiField field) {
    myField = field;
  }

  public String getText() {
    return QuickFixBundle.message("add.constructor.parameter.name");
  }

  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myField.isValid()
      && myField.getManager().isInProject(myField)
      && myField.getContainingClass() != null
      ;
  }

  public void invoke(final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;

    final PsiClass aClass = myField.getContainingClass();
    PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) {
      ApplicationManager.getApplication().runWriteAction(new Runnable(){
        public void run() {
          new AddDefaultConstructorFix(aClass).invoke(project, editor, file);
        }
      });
      constructors = aClass.getConstructors();
    }

    for (PsiMethod constructor : constructors) {
      addParameterToConstructor(project, file, editor, constructor);
    }
  }

  private void addParameterToConstructor(final Project project, final PsiFile file, final Editor editor, final PsiMethod constructor)
    throws IncorrectOperationException {
    PsiParameter[] parameters = constructor.getParameterList().getParameters();
    PsiExpression[] expressions = new PsiExpression[parameters.length+1];
    PsiElementFactory factory = file.getManager().getElementFactory();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      String value = PsiTypesUtil.getDefaultValueOfType(parameter.getType());
      expressions[i] = factory.createExpressionFromText(value, parameter);
    }
    expressions[parameters.length] = factory.createExpressionFromText(/*"this."+*/myField.getName(), constructor);

    IntentionAction addParamFix = new ChangeMethodSignatureFromUsageFix(constructor, expressions, PsiSubstitutor.EMPTY, constructor);
    addParamFix.invoke(project, editor, file);
    parameters = constructor.getParameterList().getParameters();
    final PsiParameter parameter = parameters[parameters.length-1];
    // do not introduce assignment in chanined constructor
    if (HighlightControlFlowUtil.getChainedConstructors(constructor) != null) return;
    ApplicationManager.getApplication().runWriteAction(new Runnable(){
      public void run() {
        try {
          AssignFieldFromParameterAction.addFieldAssignmentStatement(project, myField, parameter, editor);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
  }


  public boolean startInWriteAction() {
    return false;
  }
}
