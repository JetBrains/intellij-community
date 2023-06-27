// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java18StreamApi;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.PsiUpdateModCommandQuickFix;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Dmitry Batkovich
 */
public class StaticPseudoFunctionalStyleMethodInspection extends AbstractBaseJavaLocalInspectionTool {
  private final StaticPseudoFunctionalStyleMethodOptions myOptions = new StaticPseudoFunctionalStyleMethodOptions();

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!JavaFeature.STREAMS.isFeatureSupported(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression methodCallExpression) {
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
          if (h.handlerClass().equals(classQualifiedName)) {
            suitableHandler = h;
            break;
          }
        }
        if (suitableHandler == null) {
          return;
        }
        final PseudoLambdaReplaceTemplate.ValidationInfo validationInfo = suitableHandler.template().validate(methodCallExpression);
        if (validationInfo != null) {
          holder.registerProblem(methodCallExpression.getMethodExpression(),
                                 JavaBundle.message("inspection.message.pseudo.functional.style.code"),
                                 new ReplacePseudoLambdaWithLambda(suitableHandler));
        }
      }
    };
  }

  public static final class ReplacePseudoLambdaWithLambda extends PsiUpdateModCommandQuickFix {
    private final StaticPseudoFunctionalStyleMethodOptions.PipelineElement myHandler;

    private ReplacePseudoLambdaWithLambda(StaticPseudoFunctionalStyleMethodOptions.PipelineElement handler) {
      myHandler = handler;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("quickfix.family.replace.with.java.stream.api.pipeline");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement psiElement, @NotNull ModPsiUpdater updater) {
      if (psiElement instanceof PsiReferenceExpression && psiElement.getParent() instanceof PsiMethodCallExpression call) {
        myHandler.template().convertToStream(call, null, false);
      }
    }
  }
}