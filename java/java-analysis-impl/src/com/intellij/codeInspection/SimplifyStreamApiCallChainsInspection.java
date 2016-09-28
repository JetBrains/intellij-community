/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Pavel.Dolgov
 */
public class SimplifyStreamApiCallChainsInspection extends BaseJavaBatchLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#" + SimplifyStreamApiCallChainsInspection.class.getName());

  private static final String FOR_EACH_METHOD = "forEach";
  private static final String STREAM_METHOD = "stream";
  private static final String AS_LIST_METHOD = "asList";
  private static final String OF_METHOD = "of";

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression methodCall) {
        if (isCallOf(methodCall, CommonClassNames.JAVA_UTIL_COLLECTION, STREAM_METHOD, 0)) {
          final PsiMethodCallExpression qualifierCall = getQualifierMethodCall(methodCall);
          if (isCallOf(qualifierCall, CommonClassNames.JAVA_UTIL_ARRAYS, AS_LIST_METHOD, 1)) {
            final PsiExpression[] argumentExpressions = qualifierCall.getArgumentList().getExpressions();
            if (argumentExpressions.length == 1 && argumentExpressions[0].getType() instanceof PsiArrayType) {
              holder.registerProblem(methodCall, null, "Arrays.asList().stream() can be replaced with Arrays.stream()",
                                     new ArraysAsListSingleArrayFix());
            }
            else {
              holder.registerProblem(methodCall, null, "Arrays.asList().stream() can be replaced with Stream.of()",
                                     new ArraysAsListVarargFix());
            }
          }
        }
        else if (isCallOf(methodCall, CommonClassNames.JAVA_UTIL_STREAM_STREAM, FOR_EACH_METHOD, 1)) {
          final PsiMethodCallExpression qualifierCall = getQualifierMethodCall(methodCall);
          if (isCallOf(qualifierCall, CommonClassNames.JAVA_UTIL_COLLECTION, STREAM_METHOD, 0)) {
            holder.registerProblem(methodCall, getCallChainRange(methodCall, qualifierCall),
                                   "Collection.stream().forEach() can be replaced with Collection.forEach()",
                                   new CollectionForEachFix());
          }
        }
      }
    };
  }

  private static PsiMethodCallExpression getQualifierMethodCall(PsiMethodCallExpression methodCall) {
    final PsiExpression qualifierExpression = methodCall.getMethodExpression().getQualifierExpression();
    if (qualifierExpression instanceof PsiMethodCallExpression) {
      return (PsiMethodCallExpression)qualifierExpression;
    }
    return null;
  }

  @NotNull
  protected TextRange getCallChainRange(@NotNull PsiMethodCallExpression expression,
                                        @NotNull PsiMethodCallExpression qualifierExpression) {
    final PsiReferenceExpression qualifierMethodExpression = qualifierExpression.getMethodExpression();
    final PsiElement qualifierNameElement = qualifierMethodExpression.getReferenceNameElement();
    final int startOffset = (qualifierNameElement != null ? qualifierNameElement : qualifierMethodExpression).getTextOffset();
    final int endOffset = expression.getMethodExpression().getTextRange().getEndOffset();
    return new TextRange(startOffset, endOffset).shiftRight(-expression.getTextOffset());
  }

  @Contract("null, _, _, _ -> false")
  protected static boolean isCallOf(@Nullable PsiMethodCallExpression expression,
                                    @NotNull String className,
                                    @NotNull String methodName,
                                    int parametersCount) {
    if (expression == null) return false;
    final PsiMethod method = expression.resolveMethod();
    if (method != null && methodName.equals(method.getName()) && method.getParameterList().getParametersCount() == parametersCount) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null && className.equals(containingClass.getQualifiedName())) {
        return true;
      }
    }
    return false;
  }

  private static abstract class CallChainFixBase implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getStartElement();
      if (element instanceof PsiMethodCallExpression) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(element.getContainingFile())) return;
        final PsiMethodCallExpression expression = (PsiMethodCallExpression)element;
        final PsiExpression forEachMethodQualifier = expression.getMethodExpression().getQualifierExpression();
        if (forEachMethodQualifier instanceof PsiMethodCallExpression) {
          final PsiMethodCallExpression previousExpression = (PsiMethodCallExpression)forEachMethodQualifier;
          final PsiExpression qualifierExpression = previousExpression.getMethodExpression().getQualifierExpression();

          if (qualifierExpression != null) {
            final String text = createExpressionText(expression, previousExpression, qualifierExpression);
            final PsiExpression newElement;
            try {
              newElement = JavaPsiFacade.getElementFactory(project).createExpressionFromText(text, null);
            }
            catch (IncorrectOperationException e) {
              LOG.info("Failed to parse expression '" + text + "'", e);
              return;
            }
            final PsiElement shortenedElement = JavaCodeStyleManager.getInstance(project).shortenClassReferences(newElement);
            element.replace(shortenedElement);
          }
        }
      }
    }

    @NotNull
    protected abstract String createExpressionText(@NotNull PsiMethodCallExpression methodCall,
                                                   @NotNull PsiMethodCallExpression qualifierCall,
                                                   @NotNull PsiExpression qualifierExpression);
  }

  private static class ArraysAsListVarargFix extends CallChainFixBase {

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace Arrays.asList().stream() with Stream.of()";
    }

    @NotNull
    protected String createExpressionText(@NotNull PsiMethodCallExpression methodCall,
                                          @NotNull PsiMethodCallExpression qualifierCall,
                                          @NotNull PsiExpression qualifierExpression) {
      return (CommonClassNames.JAVA_UTIL_STREAM_STREAM + "." + OF_METHOD) + qualifierCall.getArgumentList().getText();
    }
  }

  private static class ArraysAsListSingleArrayFix extends CallChainFixBase {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace Arrays.asList().stream() with Arrays.stream()";
    }

    @NotNull
    protected String createExpressionText(@NotNull PsiMethodCallExpression methodCall,
                                          @NotNull PsiMethodCallExpression qualifierCall,
                                          @NotNull PsiExpression qualifierExpression) {
      return (CommonClassNames.JAVA_UTIL_ARRAYS + "." + STREAM_METHOD) + qualifierCall.getArgumentList().getText();
    }
  }

  private static class CollectionForEachFix extends CallChainFixBase {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace Collection.stream().forEach() with Collection.forEach()";
    }

    @NotNull
    @Override
    protected String createExpressionText(@NotNull PsiMethodCallExpression methodCall,
                                          @NotNull PsiMethodCallExpression qualifierCall,
                                          @NotNull PsiExpression qualifierExpression) {
      return qualifierExpression.getText() + "." + FOR_EACH_METHOD + methodCall.getArgumentList().getText();
    }
  }
}
