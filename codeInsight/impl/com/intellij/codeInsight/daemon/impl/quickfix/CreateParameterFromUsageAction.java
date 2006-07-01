package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mike
 */
public class CreateParameterFromUsageAction extends CreateVarFromUsageAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateParameterFromUsageAction");

  public CreateParameterFromUsageAction(PsiReferenceExpression referenceElement) {
    super(referenceElement);
  }

  protected boolean isAvailableImpl(int offset) {
    if (!super.isAvailableImpl(offset)) return false;
    if(!!myReferenceExpression.isQualified()) return false;
    PsiElement scope = myReferenceExpression;
    do {
      scope = PsiTreeUtil.getParentOfType(scope, PsiMethod.class, PsiClass.class);
      if (!(scope instanceof PsiAnonymousClass)) {
        return scope instanceof PsiMethod &&
               ((PsiMethod)scope).getParameterList().isPhysical();
      }
    }
    while (true);
  }

    public String getText(String varName) {
    return QuickFixBundle.message("create.parameter.from.usage.text", varName);
  }

  protected void invokeImpl(PsiClass targetClass) {
    if (CreateFromUsageUtils.isValidReference(myReferenceExpression, true)) {
      return;
    }

    PsiManager psiManager = myReferenceExpression.getManager();
    Project project = psiManager.getProject();
    PsiElementFactory factory = psiManager.getElementFactory();


    PsiType[] expectedTypes = CreateFromUsageUtils.guessType(myReferenceExpression, false);
    PsiType type = expectedTypes[0];

    String varName = myReferenceExpression.getReferenceName();
    PsiMethod method = PsiTreeUtil.getParentOfType(myReferenceExpression, PsiMethod.class);
    LOG.assertTrue(method != null);
    PsiParameter param;
    try {
      param = factory.createParameter(varName, type);
      final PsiReferenceExpression[] expressionOccurences = CreateFromUsageUtils.collectExpressions(myReferenceExpression, true, PsiMethod.class);
      param.getModifierList().setModifierProperty(PsiModifier.FINAL, CodeStyleSettingsManager.getSettings(project).GENERATE_FINAL_PARAMETERS &&
                                                                     !CreateFromUsageUtils.isAccessedForWriting(expressionOccurences));

      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length > 0 && parameters[parameters.length - 1].isVarArgs()) {
        param = (PsiParameter)method.getParameterList().addBefore(param, parameters[parameters.length - 1]);
      } else {
        param = (PsiParameter) method.getParameterList().add(param);
      }
    } catch (IncorrectOperationException e) {
      LOG.error(e);
      return;
    }

    TemplateBuilder builder = new TemplateBuilder (method);
    builder.replaceElement(param.getTypeElement(), new TypeExpression(project, expectedTypes));
    builder.setEndVariableAfter(method.getParameterList());

    method = CodeInsightUtil.forcePsiPostprocessAndRestoreElement(method);
    Template template = builder.buildTemplate();
    TextRange range = method.getTextRange();
    final PsiFile psiFile = method.getContainingFile();
    Editor editor = positionCursor(project, psiFile, method);
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    TemplateManager.getInstance(project).startTemplate(editor, template);
  }

  protected boolean isAllowOuterTargetClass() {
    return false;
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.parameter.from.usage.family");
  }

}
