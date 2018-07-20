// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.function.UnaryOperator;

public class AnonymousHasLambdaAlternativeInspection extends AbstractBaseJavaLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance(AnonymousHasLambdaAlternativeInspection.class);

  static final class AnonymousLambdaAlternative {
    final String myClassName;
    final String myMethodName;
    final String myLambdaAlternative;
    final String myReplacementMessage;

    public AnonymousLambdaAlternative(String className, String methodName, String lambdaAlternative, String replacementMessage) {
      myClassName = className;
      myMethodName = methodName;
      myLambdaAlternative = lambdaAlternative;
      myReplacementMessage = replacementMessage;
    }
  }

  private static final AnonymousLambdaAlternative[] ALTERNATIVES = {
    new AnonymousLambdaAlternative("java.lang.ThreadLocal", "initialValue", "java.lang.ThreadLocal.withInitial($lambda$)",
                                   "ThreadLocal.withInitial()"),
    new AnonymousLambdaAlternative("java.lang.Thread", "run", "new java.lang.Thread($lambda$)",
                                   "new Thread(() -> {…})")
  };

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!JavaFeature.THREAD_LOCAL_WITH_INITIAL.isFeatureSupported(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitAnonymousClass(final PsiAnonymousClass aClass) {
        super.visitAnonymousClass(aClass);
        PsiExpressionList argumentList = aClass.getArgumentList();
        if (AnonymousCanBeLambdaInspection.isLambdaForm(aClass, Collections.emptySet()) &&
            argumentList != null && argumentList.isEmpty()) {
          PsiMethod method = aClass.getMethods()[0];
          PsiClassType type = aClass.getBaseClassType();
          AnonymousLambdaAlternative alternative = getAlternative(type.resolve(), method);
          if(alternative != null) {
            final PsiElement lBrace = aClass.getLBrace();
            LOG.assertTrue(lBrace != null);
            final TextRange rangeInElement = new TextRange(0, lBrace.getStartOffsetInParent() + aClass.getStartOffsetInParent() - 1);
            holder.registerProblem(aClass.getParent(), "Anonymous #ref #loc can be replaced with "+alternative.myReplacementMessage,
                                   ProblemHighlightType.LIKE_UNUSED_SYMBOL, rangeInElement, new ReplaceWithLambdaAlternativeFix(alternative));
          }
        }
      }

      @Contract("null, _ -> null")
      private AnonymousLambdaAlternative getAlternative(PsiClass type, PsiMethod method) {
        if(type == null) return null;
        for(AnonymousLambdaAlternative alternative : ALTERNATIVES) {
          if(alternative.myClassName.equals(type.getQualifiedName()) && alternative.myMethodName.equals(method.getName())) {
            return alternative;
          }
        }
        return null;
      }
    };
  }

  static class ReplaceWithLambdaAlternativeFix implements LocalQuickFix {
    private final @NotNull AnonymousLambdaAlternative myAlternative;

    public ReplaceWithLambdaAlternativeFix(@NotNull AnonymousLambdaAlternative alternative) {
      myAlternative = alternative;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Replace with "+myAlternative.myReplacementMessage;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace anonymous class with lambda alternative";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if(!(element instanceof PsiNewExpression)) return;
      PsiAnonymousClass aClass = ((PsiNewExpression)element).getAnonymousClass();
      if(aClass == null) return;
      PsiMethod[] methods = aClass.getMethods();
      if(methods.length != 1) return;
      PsiMethod method = methods[0];
      if(method.getBody() == null) return;
      UnaryOperator<PsiLambdaExpression> replacer = lambda -> {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiElement replacement = element.replace(factory.createExpressionFromText(myAlternative.myLambdaAlternative, element));
        PsiElement[] lambdaPositions =
          PsiTreeUtil.collectElements(replacement, e -> e instanceof PsiReference && e.textMatches("$lambda$"));
        LOG.assertTrue(lambdaPositions.length == 1);
        return (PsiLambdaExpression)lambdaPositions[0].replace(lambda);
      };
      AnonymousCanBeLambdaInspection.generateLambdaByMethod(aClass, method, replacer, true);
    }
  }
}
