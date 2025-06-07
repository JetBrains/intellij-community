// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;
import java.util.function.UnaryOperator;

public final class AnonymousHasLambdaAlternativeInspection extends AbstractBaseJavaLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance(AnonymousHasLambdaAlternativeInspection.class);

  static final class AnonymousLambdaAlternative {
    final String myClassName;
    final String myMethodName;
    final String myLambdaAlternative;
    final String myReplacementMessage;

    AnonymousLambdaAlternative(@NonNls String className,
                               @NonNls String methodName,
                               @NonNls String lambdaAlternative,
                               @NonNls String replacementMessage) {
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

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.THREAD_LOCAL_WITH_INITIAL);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitAnonymousClass(final @NotNull PsiAnonymousClass aClass) {
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
            holder.registerProblem(aClass.getParent(), rangeInElement,
                                   JavaAnalysisBundle.message("anonymous.ref.loc.can.be.replaced.with.0", alternative.myReplacementMessage), new ReplaceWithLambdaAlternativeFix(alternative));
          }
        }
      }

      @Contract("null, _ -> null")
      private static AnonymousLambdaAlternative getAlternative(PsiClass type, PsiMethod method) {
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

  static class ReplaceWithLambdaAlternativeFix extends PsiUpdateModCommandQuickFix {
    private final @NotNull AnonymousLambdaAlternative myAlternative;

    ReplaceWithLambdaAlternativeFix(@NotNull AnonymousLambdaAlternative alternative) {
      myAlternative = alternative;
    }

    @Override
    public @Nls @NotNull String getName() {
      return JavaAnalysisBundle.message("replace.with.0", myAlternative.myReplacementMessage);
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return JavaAnalysisBundle.message("replace.anonymous.class.with.lambda.alternative");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if(!(element instanceof PsiNewExpression newExpression)) return;
      PsiAnonymousClass aClass = newExpression.getAnonymousClass();
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
