package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Mike
 */
public class CreateFieldFromUsageAction extends CreateVarFromUsageAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateFieldFromUsageAction");

  public CreateFieldFromUsageAction(PsiReferenceExpression referenceElement) {
    super(referenceElement);
  }

  protected String getText(String varName) {
    return QuickFixBundle.message("create.field.from.usage.text", varName);
  }

  protected boolean createConstantField() {
    return false;
  }

  protected void invokeImpl(PsiClass targetClass) {
    if (CreateFromUsageUtils.isValidReference(myReferenceExpression, true)) {
      return;
    }

    PsiManager psiManager = myReferenceExpression.getManager();
    Project project = psiManager.getProject();
    PsiElementFactory factory = psiManager.getElementFactory();


    PsiMember enclosingContext = null;
    PsiClass parentClass;
    do {
      enclosingContext = PsiTreeUtil.getParentOfType(
        enclosingContext == null ? myReferenceExpression : enclosingContext,
        PsiMethod.class, PsiField.class, PsiClassInitializer.class);
      parentClass = enclosingContext == null ? null : enclosingContext.getContainingClass();
    }
    while (parentClass instanceof PsiAnonymousClass);

    PsiFile targetFile = targetClass.getContainingFile();

    try {
      ExpectedTypeInfo[] expectedTypes = CreateFromUsageUtils.guessExpectedTypes(myReferenceExpression, false);

      String fieldName = myReferenceExpression.getReferenceName();
      PsiField field;
      if (!createConstantField()) {
        field = factory.createField(fieldName, PsiType.INT);
      } else {
        PsiClass aClass = factory.createClassFromText("int i = 0;", null);
        field = aClass.getFields()[0];
        field.setName(fieldName);
      }
      if (enclosingContext != null && enclosingContext.getParent() == parentClass && targetClass == parentClass
          && (enclosingContext instanceof PsiClassInitializer || enclosingContext instanceof PsiField)) {
        field = (PsiField)targetClass.addBefore(field, enclosingContext);
      }
      else {
        field = (PsiField)targetClass.add(field);
      }

      setupVisibility(parentClass, targetClass, field.getModifierList());

      if (shouldCreateStaticMember(myReferenceExpression, enclosingContext, targetClass)) {
        field.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
      }

      if (createConstantField()) {
        field.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
        field.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
      }

      TemplateBuilder builder = new TemplateBuilder(field);
      PsiElement context = PsiTreeUtil.getParentOfType(myReferenceExpression, PsiClass.class, PsiMethod.class);
      new GuessTypeParameters(factory).setupTypeElement(field.getTypeElement(), expectedTypes, getTargetSubstitutor(myReferenceExpression), builder, context, targetClass);

      if (createConstantField()) {
        builder.replaceElement(field.getInitializer(), new EmptyExpression());
      }

      builder.setEndVariableAfter(field.getNameIdentifier());
      field = CodeInsightUtil.forcePsiPostprocessAndRestoreElement(field);
      Template template = builder.buildTemplate();

      Editor newEditor = positionCursor(project, targetFile, field);
      TextRange range = field.getTextRange();
      newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

      startTemplate(newEditor, template, project);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public String getFamilyName() {
    return QuickFixBundle.message("create.field.from.usage.family");
  }
}
