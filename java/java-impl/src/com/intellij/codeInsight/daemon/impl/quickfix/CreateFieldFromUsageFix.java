/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mike
 */
public class CreateFieldFromUsageFix extends CreateVarFromUsageFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateFieldFromUsageFix");

  public CreateFieldFromUsageFix(PsiReferenceExpression referenceElement) {
    super(referenceElement);
  }

  protected String getText(String varName) {
    return QuickFixBundle.message("create.field.from.usage.text", varName);
  }

  protected boolean createConstantField() {
    return false;
  }

  protected void invokeImpl(final PsiClass targetClass) {
    if (CreateFromUsageUtils.isValidReference(myReferenceExpression, true)) {
      return;
    }

    final Project project = myReferenceExpression.getProject();
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();

    PsiMember enclosingContext = null;
    PsiClass parentClass;
    do {
      enclosingContext = PsiTreeUtil.getParentOfType(enclosingContext == null ? myReferenceExpression : enclosingContext, PsiMethod.class,
                                    PsiField.class, PsiClassInitializer.class);
      parentClass = enclosingContext == null ? null : enclosingContext.getContainingClass();
    }
    while (parentClass instanceof PsiAnonymousClass);

    final PsiFile targetFile = targetClass.getContainingFile();

    ExpectedTypeInfo[] expectedTypes = CreateFromUsageUtils.guessExpectedTypes(myReferenceExpression, false);

    String fieldName = myReferenceExpression.getReferenceName();
    PsiField field;
    if (!createConstantField()) {
      field = factory.createField(fieldName, PsiType.INT);
    }
    else {
      PsiClass aClass = factory.createClassFromText("int i = 0;", null);
      field = aClass.getFields()[0];
      field.setName(fieldName);
    }
    if (enclosingContext != null && enclosingContext.getParent() == parentClass && targetClass == parentClass
        && enclosingContext instanceof PsiField) {
      field = (PsiField)targetClass.addBefore(field, enclosingContext);
    }
    else if (enclosingContext != null && enclosingContext.getParent() == parentClass && targetClass == parentClass
             && enclosingContext instanceof PsiClassInitializer) {
      field = (PsiField)targetClass.addBefore(field, enclosingContext);
      targetClass.addBefore(CodeEditUtil.createLineFeed(field.getManager()), enclosingContext);
    }
    else {
      field = (PsiField)targetClass.add(field);
    }

    setupVisibility(parentClass, targetClass, field.getModifierList());

    if (shouldCreateStaticMember(myReferenceExpression, targetClass)) {
      PsiUtil.setModifierProperty(field, PsiModifier.STATIC, true);
    }

    if (createConstantField()) {
      PsiUtil.setModifierProperty(field, PsiModifier.STATIC, true);
      PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true);
    }

    TemplateBuilderImpl builder = new TemplateBuilderImpl(field);
    PsiElement context = PsiTreeUtil.getParentOfType(myReferenceExpression, PsiClass.class, PsiMethod.class);
    new GuessTypeParameters(factory)
      .setupTypeElement(field.getTypeElement(), expectedTypes, getTargetSubstitutor(myReferenceExpression),
                        builder, context, targetClass);

    if (createConstantField()) {
      builder.replaceElement(field.getInitializer(), new EmptyExpression());
    }

    builder.setEndVariableAfter(field.getNameIdentifier());
    field = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(field);
    Template template = builder.buildTemplate();

    final Editor newEditor = positionCursor(project, targetFile, field);
    TextRange range = field.getTextRange();
    newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    template.setToShortenLongNames(false);

    startTemplate(newEditor, template, project, new TemplateEditingAdapter() {
      @Override
      public void templateFinished(Template template, boolean brokenOff) {
        PsiDocumentManager.getInstance(project).commitDocument(newEditor.getDocument());
        final int offset = newEditor.getCaretModel().getOffset();
        final PsiField psiField = PsiTreeUtil.findElementOfClassAtOffset(targetFile, offset, PsiField.class, false);
        if (psiField != null) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              CodeStyleManager.getInstance(project).reformat(psiField);
            }
          });
        }
      }
    });
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.field.from.usage.family");
  }
}
