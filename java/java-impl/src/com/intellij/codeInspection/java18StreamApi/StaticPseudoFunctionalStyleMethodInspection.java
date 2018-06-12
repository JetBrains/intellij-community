// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.java18StreamApi;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @author Dmitry Batkovich
 */
public class StaticPseudoFunctionalStyleMethodInspection extends AbstractBaseJavaLocalInspectionTool {
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
    if (!JavaFeature.STREAMS.isFeatureSupported(holder.getFile())) {
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

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with Java Stream API pipeline";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      if (psiElement instanceof PsiReferenceExpression) {
        PsiElement parent = psiElement.getParent();
        if (parent instanceof PsiMethodCallExpression) {
          myHandler.getTemplate().convertToStream((PsiMethodCallExpression)parent, null, false);
        }
      }
    }
  }
}