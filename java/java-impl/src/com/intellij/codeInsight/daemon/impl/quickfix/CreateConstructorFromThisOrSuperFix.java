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

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.CreateFromUsage;
import com.intellij.codeInsight.intention.JvmCommonIntentionActionsFactory;
import com.intellij.codeInsight.intention.impl.JavaCommonIntentionActionsFactory;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import java.util.Collections;
import java.util.List;

public abstract class CreateConstructorFromThisOrSuperFix extends CreateFromUsageBaseFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateConstructorFromThisOrSuperFix");

  protected PsiMethodCallExpression myMethodCall;

  public CreateConstructorFromThisOrSuperFix(PsiMethodCallExpression methodCall) {
    myMethodCall = methodCall;
  }

  @NonNls
  protected abstract String getSyntheticMethodName ();

  @Override
  protected boolean isAvailableImpl(int offset) {
    PsiReferenceExpression ref = myMethodCall.getMethodExpression();
    if (!ref.getText().equals(getSyntheticMethodName())) return false;

    PsiMethod method = PsiTreeUtil.getParentOfType(myMethodCall, PsiMethod.class);
    if (method == null || !method.isConstructor()) return false;
    if (CreateMethodFromUsageFix.hasErrorsInArgumentList(myMethodCall)) return false;
    List<PsiClass> targetClasses = getTargetClasses(myMethodCall);
    if (targetClasses.isEmpty()) return false;

    if (CreateFromUsageUtils.shouldShowTag(offset, ref.getReferenceNameElement(), myMethodCall)) {
      setText(QuickFixBundle.message("create.constructor.text", targetClasses.get(0).getName()));
      return true;
    }

    return false;
  }

  @Override
  protected void invokeImpl(PsiClass targetClass) {
    final PsiFile callSite = myMethodCall.getContainingFile();
    final Project project = myMethodCall.getProject();
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

    try {
      JvmCommonIntentionActionsFactory actionsFactory = JvmCommonIntentionActionsFactory.forLanguage(targetClass.getLanguage());
      if (actionsFactory != null && !(actionsFactory instanceof JavaCommonIntentionActionsFactory)) {
        PsiExpression[] arguments = myMethodCall.getArgumentList().getExpressions();
        CreateFromUsage.ConstructorInfo constructorInfo = new CreateFromUsage.ConstructorInfo(
          targetClass,
          Collections.emptyList(),
          CreateFromUsageUtils.getParameterInfos(targetClass, ContainerUtil.map2List(arguments, Pair.createFunction(null)))
        );
        CreateFromUsageUtils.invokeActionInTargetEditor(targetClass,
                                                        () -> actionsFactory.createGenerateConstructorFromUsageActions(constructorInfo));
        return;
      }

      PsiMethod constructor = elementFactory.createConstructor();
      constructor = (PsiMethod)targetClass.add(constructor);

      final TemplateBuilderImpl templateBuilder = new TemplateBuilderImpl(constructor);
      CreateFromUsageUtils.setupMethodParameters(constructor, templateBuilder, myMethodCall.getArgumentList(),
                                                  getTargetSubstitutor(myMethodCall));

      final PsiFile psiFile = myMethodCall.getContainingFile();

      templateBuilder.setEndVariableAfter(constructor.getBody().getLBrace());
      final RangeMarker rangeMarker = psiFile.getViewProvider().getDocument().createRangeMarker(myMethodCall.getTextRange());

      constructor = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(constructor);

      targetClass = constructor.getContainingClass();
      myMethodCall = CodeInsightUtil.findElementInRange(psiFile, rangeMarker.getStartOffset(), rangeMarker.getEndOffset(), myMethodCall.getClass());
      rangeMarker.dispose();

      Template template = templateBuilder.buildTemplate();
      final Editor editor = positionCursor(project, targetClass.getContainingFile(), targetClass);
      if (editor == null) return;
      final TextRange textRange = constructor.getTextRange();
      final PsiFile file = targetClass.getContainingFile();
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
              CreateFromUsageUtils.setupMethodBody(constructor1);
              CreateFromUsageUtils.setupEditor(constructor1, editor);

              UndoUtil.markPsiFileForUndo(callSite);
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

  @Override
  protected boolean isValidElement(PsiElement element) {
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression) element;
    PsiMethod method = (PsiMethod) methodCall.getMethodExpression().resolve();
    PsiExpressionList argumentList = methodCall.getArgumentList();
    List<PsiClass> classes = getTargetClasses(element);
    return !classes.isEmpty() && !CreateFromUsageUtils.shouldCreateConstructor(classes.get(0), argumentList, method);
  }

  @Override
  protected PsiElement getElement() {
    if (!myMethodCall.isValid() || !myMethodCall.getManager().isInProject(myMethodCall)) return null;
    return myMethodCall;
  }
}
