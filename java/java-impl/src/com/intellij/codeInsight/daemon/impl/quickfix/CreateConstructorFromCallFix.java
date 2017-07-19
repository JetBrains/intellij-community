/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.intention.CreateFromUsage;
import com.intellij.codeInsight.intention.JvmCommonIntentionActionsFactory;
import com.intellij.codeInsight.intention.impl.JavaCommonIntentionActionsFactory;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author mike
 */
public class CreateConstructorFromCallFix extends CreateFromUsageBaseFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateConstructorFromCallFix");

  private final PsiConstructorCall myConstructorCall;

  public CreateConstructorFromCallFix(@NotNull PsiConstructorCall constructorCall) {
    myConstructorCall = constructorCall;
  }

  @Override
  protected boolean canBeTargetClass(PsiClass psiClass) {
    return false;
  }

  @Override
  protected void invokeImpl(final PsiClass targetClass) {
    final Project project = myConstructorCall.getProject();

    JvmCommonIntentionActionsFactory actionsFactory = JvmCommonIntentionActionsFactory.forLanguage(targetClass.getLanguage());
    if (actionsFactory != null && !(actionsFactory instanceof JavaCommonIntentionActionsFactory)) {
      PsiExpressionList argumentList = myConstructorCall.getArgumentList();
      PsiExpression[] arguments = argumentList != null ? argumentList.getExpressions() : PsiExpression.EMPTY_ARRAY;
      CreateFromUsage.ConstructorInfo constructorInfo = new CreateFromUsage.ConstructorInfo(
        targetClass,
        Collections.emptyList(),
        CreateFromUsageUtils.getParameterInfos(targetClass, ContainerUtil.map2List(arguments, Pair.createFunction(null)))
      );
      CreateFromUsageUtils.invokeActionInTargetEditor(targetClass,
                                                      () -> actionsFactory.createGenerateConstructorFromUsageActions(constructorInfo));
      return;
    }

    JVMElementFactory elementFactory = JVMElementFactories.getFactory(targetClass.getLanguage(), project);
    if (elementFactory == null) elementFactory = JavaPsiFacade.getElementFactory(project);

    try {
      PsiMethod constructor = (PsiMethod)targetClass.add(elementFactory.createConstructor());

      final PsiFile file = targetClass.getContainingFile();
      TemplateBuilderImpl templateBuilder = new TemplateBuilderImpl(constructor);
      CreateFromUsageUtils.setupMethodParameters(constructor, templateBuilder, myConstructorCall.getArgumentList(),
                                                 getTargetSubstitutor(myConstructorCall));
      final PsiMethod superConstructor = CreateClassFromNewFix.setupSuperCall(targetClass, constructor, templateBuilder);

      constructor = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(constructor);
      Template template = templateBuilder.buildTemplate();
      final Editor editor = positionCursor(project, targetClass.getContainingFile(), targetClass);
      if (editor == null) return;
      final TextRange textRange = constructor.getTextRange();
      editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());
      editor.getCaretModel().moveToOffset(textRange.getStartOffset());

      startTemplate(editor, template, project, new TemplateEditingAdapter() {
        @Override
        public void templateFinished(Template template, boolean brokenOff) {
          ApplicationManager.getApplication().runWriteAction(() -> {
            try {
              PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
              final int offset = editor.getCaretModel().getOffset();
              PsiMethod constructor1 = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiMethod.class, false);
              if (superConstructor == null) {
                CreateFromUsageUtils.setupMethodBody(constructor1);
              } else {
                OverrideImplementUtil.setupMethodBody(constructor1, superConstructor, targetClass);
              }
              CreateFromUsageUtils.setupEditor(constructor1, editor);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          });
        }
      });
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static PsiFile getTargetFile(PsiElement element) {
    final PsiConstructorCall constructorCall = (PsiConstructorCall)element;

    //Enum constants constructors are file local
    if (constructorCall instanceof PsiEnumConstant) return constructorCall.getContainingFile();

    PsiJavaCodeReferenceElement referenceElement = getReferenceElement(constructorCall);
    if (referenceElement.getQualifier() instanceof PsiJavaCodeReferenceElement) {
      PsiJavaCodeReferenceElement qualifier = (PsiJavaCodeReferenceElement)referenceElement.getQualifier();
      PsiElement psiElement = qualifier.resolve();
      if (psiElement instanceof PsiClass) {
        PsiClass psiClass = (PsiClass)psiElement;
        return psiClass.getContainingFile();
      }
    }

    return null;
  }

  @Override
  protected PsiElement getElement() {
    if (!myConstructorCall.isValid() || !myConstructorCall.getManager().isInProject(myConstructorCall)) return null;

    PsiExpressionList argumentList = myConstructorCall.getArgumentList();
    if (argumentList == null) return null;

    if (myConstructorCall instanceof PsiEnumConstant) return myConstructorCall;

    PsiJavaCodeReferenceElement referenceElement = getReferenceElement(myConstructorCall);
    if (referenceElement == null) return null;
    if (referenceElement.getReferenceNameElement() instanceof PsiIdentifier) return myConstructorCall;

    return null;
  }

  @Override
  protected boolean isValidElement(PsiElement element) {
    PsiConstructorCall constructorCall = (PsiConstructorCall)element;
    PsiMethod method = constructorCall.resolveConstructor();
    PsiExpressionList argumentList = constructorCall.getArgumentList();
    List<PsiClass> targetClasses = getTargetClasses(constructorCall);
    if (targetClasses.isEmpty()) return false;
    PsiClass targetClass = targetClasses.get(0);

    return !CreateFromUsageUtils.shouldCreateConstructor(targetClass, argumentList, method);
  }

  @Override
  protected boolean isAvailableImpl(int offset) {
    PsiElement element = getElement(myConstructorCall);

    PsiFile targetFile = getTargetFile(myConstructorCall);
    if (targetFile != null && !targetFile.getManager().isInProject(targetFile)) {
      return false;
    }

    if (CreateFromUsageUtils.shouldShowTag(offset, element, myConstructorCall)) {
      setText(QuickFixBundle.message("create.constructor.from.new.text"));
      return true;
    }

    return false;
  }

  private static PsiJavaCodeReferenceElement getReferenceElement(PsiConstructorCall constructorCall) {
    if (constructorCall instanceof PsiNewExpression) {
      return ((PsiNewExpression)constructorCall).getClassOrAnonymousClassReference();
    }
    return null;
  }

  private static PsiElement getElement(PsiElement targetElement) {
    if (targetElement instanceof PsiNewExpression) {
      PsiJavaCodeReferenceElement referenceElement = getReferenceElement((PsiNewExpression)targetElement);
      if (referenceElement == null) return null;
      return referenceElement.getReferenceNameElement();
    }
    else if (targetElement instanceof PsiEnumConstant) {
      return targetElement;
    }

    return null;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.constructor.from.new.family");
  }
}
