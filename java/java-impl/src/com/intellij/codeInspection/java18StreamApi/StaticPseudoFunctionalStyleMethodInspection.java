/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInspection.java18StreamApi;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @author Dmitry Batkovich
 */
public class StaticPseudoFunctionalStyleMethodInspection extends BaseJavaBatchLocalInspectionTool {
  private final static Logger LOG = Logger.getInstance(StaticPseudoFunctionalStyleMethodInspection.class);
  private final StaticPseudoFunctionalStyleMethodOptions myOptions = new StaticPseudoFunctionalStyleMethodOptions();

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    myOptions.readExternal(node);
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    myOptions.writeExternal(node);
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return myOptions.createPanel();
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression methodCallExpression) {
        String qName = methodCallExpression.getMethodExpression().getQualifiedName();
        if (qName == null) {
          return;
        }
        qName = StringUtil.getShortName(qName);
        final Collection<StaticPseudoFunctionalStyleMethodOptions.PipelineElement> handlerInfos = myOptions.findElementsByMethodName(qName);
        if (handlerInfos.isEmpty()) {
          return;
        }
        final PsiMethod method = methodCallExpression.resolveMethod();
        if (method == null) {
          return;
        }
        final PsiClass aClass = method.getContainingClass();
        if (aClass == null) {
          return;
        }
        final String classQualifiedName = aClass.getQualifiedName();
        if (classQualifiedName == null) {
          return;
        }
        StaticPseudoFunctionalStyleMethodOptions.PipelineElement suitableHandler = null;
        for (StaticPseudoFunctionalStyleMethodOptions.PipelineElement h : handlerInfos) {
          if (h.getHandlerClass().equals(classQualifiedName)) {
            suitableHandler = h;
            break;
          }
        }
        if (suitableHandler == null) {
          return;
        }
        final PseudoLambdaReplaceTemplate.ValidationInfo validationInfo = suitableHandler.getTemplate().validate(methodCallExpression);
        if (validationInfo != null) {
          holder.registerProblem(methodCallExpression.getMethodExpression(),
                                 "Pseudo functional style code",
                                 new ReplacePseudoLambdaWithLambda(suitableHandler));
        }
      }
    };
  }

  public static class ReplacePseudoLambdaWithLambda implements LocalQuickFix {
    private final StaticPseudoFunctionalStyleMethodOptions.PipelineElement myHandler;

    private ReplacePseudoLambdaWithLambda(StaticPseudoFunctionalStyleMethodOptions.PipelineElement handler) {
      myHandler = handler;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with Java Stream API pipeline";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      if (!FileModificationService.getInstance().preparePsiElementsForWrite(psiElement)) {
        return;
      }
      if (psiElement instanceof PsiReferenceExpression) {
        PsiElement parent = psiElement.getParent();
        if (parent instanceof PsiMethodCallExpression) {
          myHandler.getTemplate().convertToStream((PsiMethodCallExpression)parent, null, false);
        }
      }
    }
  }
}