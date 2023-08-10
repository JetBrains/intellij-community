// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.intentions;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationBundle;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author anna
 */
public class ChangeClassParametersIntention extends PsiElementBaseIntentionAction {

  private static final Logger LOG = Logger.getInstance(ChangeClassParametersIntention.class);

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return TypeMigrationBundle.message("change.class.type.parameter.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    final PsiTypeElement typeElement = PsiTreeUtil.getTopmostParentOfType(element, PsiTypeElement.class);
    final PsiElement parent = typeElement != null ? typeElement.getParent() : null;
    final PsiReferenceParameterList parameterList = parent instanceof PsiReferenceParameterList ? (PsiReferenceParameterList)parent : null;
    if (parameterList != null && parameterList.getTypeArguments().length > 0) {
      final PsiMember member = PsiTreeUtil.getParentOfType(parameterList, PsiMember.class);
      if (member instanceof PsiAnonymousClass) {
        final PsiClassType.ClassResolveResult result = ((PsiAnonymousClass)member).getBaseClassType().resolveGenerics();
        final PsiClass baseClass = result.getElement();
        return baseClass != null && baseClass.getTypeParameters().length == parameterList.getTypeParameterElements().length &&
               ((PsiAnonymousClass)member).getBaseClassReference().getParameterList() == parameterList;
      }
    }
    return false;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) throws IncorrectOperationException {
    final PsiTypeElement typeElement = PsiTreeUtil.getTopmostParentOfType(element, PsiTypeElement.class);
    final PsiReferenceParameterList parameterList = PsiTreeUtil.getParentOfType(typeElement, PsiReferenceParameterList.class);
    if (parameterList != null) {
      final PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      if (aClass instanceof PsiAnonymousClass) {
        editor.getCaretModel().moveToOffset(aClass.getTextOffset());
        final PsiTypeElement[] typeElements = parameterList.getTypeParameterElements();
        final int changeIdx = ArrayUtil.find(typeElements, typeElement);

        final PsiClassType.ClassResolveResult result = ((PsiAnonymousClass)aClass).getBaseClassType().resolveGenerics();
        final PsiClass baseClass = result.getElement();
        LOG.assertTrue(baseClass != null);
        final PsiTypeParameter typeParameter = baseClass.getTypeParameters()[changeIdx];

        final TemplateBuilderImpl templateBuilder = (TemplateBuilderImpl)TemplateBuilderFactory.getInstance().createTemplateBuilder(aClass);

        final String oldTypeText = typeElement.getText();
        final @NonNls String varName = "param";
        templateBuilder.replaceElement(typeElement, varName, new TypeExpression(project, new PsiType[]{typeElement.getType()}), true);

        final Template template = templateBuilder.buildInlineTemplate();
        TemplateManager.getInstance(project).startTemplate(editor, template, false, null, new TemplateEditingAdapter() {
          private String myNewType;

          @Override
          public void beforeTemplateFinished(@NotNull TemplateState state, Template template) {
            final TextResult value = state.getVariableValue(varName);
            myNewType = value != null ? value.getText() : "";
            final int segmentsCount = state.getSegmentsCount();
            final Document document = state.getEditor().getDocument();
            ApplicationManager.getApplication().runWriteAction(() -> {
              for (int i = 0; i < segmentsCount; i++) {
                final TextRange segmentRange = state.getSegmentRange(i);
                document.replaceString(segmentRange.getStartOffset(), segmentRange.getEndOffset(), oldTypeText);
              }
            });
          }

          @Override
          public void templateFinished(@NotNull Template template, boolean brokenOff) {
            if (!brokenOff) {
              final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
              try {
                final PsiType targetParam = elementFactory.createTypeFromText(myNewType, aClass);
                if (!(targetParam instanceof PsiClassType classType)) {
                  HintManager.getInstance().showErrorHint(editor,
                                                          JavaErrorBundle.message("generics.type.argument.cannot.be.of.primitive.type"));
                  return;
                }
                final PsiClass target = classType.resolve();
                if (target == null) {
                  HintManager.getInstance().showErrorHint(editor, JavaErrorBundle.message("cannot.resolve.symbol",
                                                                                          classType.getPresentableText()));
                  return;
                }
                final PsiSubstitutor substitutor = result.getSubstitutor().put(typeParameter, targetParam);
                final PsiType targetClassType = elementFactory.createType(baseClass, substitutor);
                var supportProvider = CommonJavaRefactoringUtil.getRefactoringSupport();
                var handler = supportProvider.getChangeTypeSignatureHandler();
                handler.runHighlightingTypeMigrationSilently(project, editor, new LocalSearchScope(aClass), ((PsiAnonymousClass)aClass).getBaseClassReference().getParameterList(), targetClassType);
              }
              catch (IncorrectOperationException e) {
                HintManager.getInstance().showErrorHint(editor, TypeMigrationBundle.message("change.class.parameter.incorrect.type.error.hint"));
              }
            }
          }
        });
      }
    }
  }
}
