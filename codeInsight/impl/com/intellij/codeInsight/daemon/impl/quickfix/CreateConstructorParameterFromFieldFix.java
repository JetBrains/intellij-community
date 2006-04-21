package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.AssignFieldFromParameterAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.IncorrectOperationException;
import com.sun.corba.se.impl.encoding.OSFCodeSetRegistry;
import org.jetbrains.annotations.NotNull;

public class CreateConstructorParameterFromFieldFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateConstructorParameterFromFieldFix");

  private SmartPsiElementPointer myField;

  public CreateConstructorParameterFromFieldFix(@NotNull PsiField field) {
    myField = SmartPointerManager.getInstance(field.getProject()).createSmartPsiElementPointer(field);
  }

  public String getText() {
    return QuickFixBundle.message("add.constructor.parameter.name");
  }

  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    final PsiField fieldElement = (PsiField)myField.getElement();
    return myField.getElement() != null
           && fieldElement.getManager().isInProject(fieldElement)
           && fieldElement.getContainingClass() != null
      ;
  }

  public void invoke(final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;

    PsiClass aClass = getField().getContainingClass();
    PsiMethod[] constructors = aClass.getConstructors();
    final RangeMarker rangeMarker = aClass.getContainingFile().getViewProvider().getDocument().createRangeMarker(aClass.getTextRange());
    if (constructors.length == 0) {
      final AddDefaultConstructorFix defaultConstructorFix = new AddDefaultConstructorFix(aClass);
      ApplicationManager.getApplication().runWriteAction(new Runnable(){
        public void run() {
          defaultConstructorFix.invoke(project, editor, file);
        }
      });
      aClass = getField().getContainingClass();
      constructors = aClass.getConstructors();
    }
    for (int i = 0; i < constructors.length; i++){
      addParameterToConstructor(project, file, editor, getField().getContainingClass().getConstructors()[i]);
    }
  }

  private void addParameterToConstructor(final Project project, final PsiFile file, final Editor editor, PsiMethod constructor)
    throws IncorrectOperationException {
    PsiParameter[] parameters = constructor.getParameterList().getParameters();
    PsiExpression[] expressions = new PsiExpression[parameters.length+1];
    PsiElementFactory factory = file.getManager().getElementFactory();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      String value = PsiTypesUtil.getDefaultValueOfType(parameter.getType());
      expressions[i] = factory.createExpressionFromText(value, parameter);
    }
    expressions[parameters.length] = factory.createExpressionFromText(/*"this."+*/getField().getName(), constructor);
    final SmartPointerManager manager = SmartPointerManager.getInstance(getField().getProject());
    final SmartPsiElementPointer constructorPointer = manager.createSmartPsiElementPointer(constructor);

    IntentionAction addParamFix = new ChangeMethodSignatureFromUsageFix(constructor, expressions, PsiSubstitutor.EMPTY, constructor);
    addParamFix.invoke(project, editor, file);
    constructor = (PsiMethod)constructorPointer.getElement();
    parameters = constructor.getParameterList().getParameters();
    final PsiParameter parameter = parameters[parameters.length-1];
    // do not introduce assignment in chanined constructor
    if (HighlightControlFlowUtil.getChainedConstructors(constructor) != null) return;
    ApplicationManager.getApplication().runWriteAction(new Runnable(){
      public void run() {
        try {
          AssignFieldFromParameterAction.addFieldAssignmentStatement(project, getField(), parameter, editor);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
  }

  private PsiField getField() {
    return (PsiField)myField.getElement();
  }

  public boolean startInWriteAction() {
    return false;
  }
}
