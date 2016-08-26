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
  private static final String FOR_EACH_ORDERED_METHOD = "forEachOrdered";
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
        else {
          final String name;
          if (isCallOf(methodCall, CommonClassNames.JAVA_UTIL_STREAM_STREAM, FOR_EACH_METHOD, 1)) {
            name = FOR_EACH_METHOD;
          }
          else if (isCallOf(methodCall, CommonClassNames.JAVA_UTIL_STREAM_STREAM, FOR_EACH_ORDERED_METHOD, 1)) {
            name = FOR_EACH_ORDERED_METHOD;
          }
          else {
            return;
          }
          final PsiMethodCallExpression qualifierCall = getQualifierMethodCall(methodCall);
          if (isCallOf(qualifierCall, CommonClassNames.JAVA_UTIL_COLLECTION, STREAM_METHOD, 0)) {
            String message = "Collection.stream()." + name + "() can be replaced with Collection.forEach()";
            final LocalQuickFix fix;
            if (FOR_EACH_METHOD.equals(name)) {
              fix = new CollectionForEachFix();
            }
            else {
              fix = new CollectionForEachOrderedFix();
              message += " (may change semantics)";
            }
            holder.registerProblem(methodCall, getCallChainRange(methodCall, qualifierCall), message, fix);
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
          replaceMethodCall(expression, previousExpression, qualifierExpression);
        }
      }
    }

    protected abstract void replaceMethodCall(@NotNull PsiMethodCallExpression methodCall,
                                              @NotNull PsiMethodCallExpression qualifierCall,
                                              @Nullable PsiExpression qualifierExpression);
  }

  private static abstract class ArraysAsListFix extends CallChainFixBase {
    private final String myClassName;
    private final String myMethodName;

    private ArraysAsListFix(String className, String methodName) {
      myClassName = className;
      myMethodName = methodName;
    }

    @Override
    protected void replaceMethodCall(@NotNull PsiMethodCallExpression methodCall,
                                     @NotNull PsiMethodCallExpression qualifierCall,
                                     @Nullable PsiExpression qualifierExpression) {
      methodCall.getArgumentList().replace(qualifierCall.getArgumentList());

      final Project project = methodCall.getProject();
      PsiType[] parameters = qualifierCall.getMethodExpression().getTypeParameters();
      String replacement;
      if(parameters.length == 1) {
        replacement = myClassName + ".<" + parameters[0].getCanonicalText() + ">" + myMethodName;
      } else {
        replacement = myClassName + "." + myMethodName;
      }
      final PsiExpression newMethodExpression = JavaPsiFacade.getElementFactory(project).createExpressionFromText(replacement, methodCall);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(methodCall.getMethodExpression().replace(newMethodExpression));
    }
  }

  private static class ArraysAsListVarargFix extends ArraysAsListFix {
    private ArraysAsListVarargFix() {
      super(CommonClassNames.JAVA_UTIL_STREAM_STREAM, OF_METHOD);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace Arrays.asList().stream() with Stream.of()";
    }
  }

  private static class ArraysAsListSingleArrayFix extends ArraysAsListFix {
    private ArraysAsListSingleArrayFix() {
      super(CommonClassNames.JAVA_UTIL_ARRAYS, STREAM_METHOD);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace Arrays.asList().stream() with Arrays.stream()";
    }
  }

  private static class CollectionForEachFix extends CallChainFixBase {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace Collection.stream()." + FOR_EACH_METHOD + "() with Collection.forEach()";
    }

    @Override
    protected void replaceMethodCall(@NotNull PsiMethodCallExpression methodCall,
                                     @NotNull PsiMethodCallExpression qualifierCall,
                                     @Nullable PsiExpression qualifierExpression) {
      if (qualifierExpression != null) {
        qualifierCall.replace(qualifierExpression);
      }
    }
  }

  private static class CollectionForEachOrderedFix extends CollectionForEachFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace Collection.stream()." + FOR_EACH_ORDERED_METHOD + "() with Collection.forEach() (may change semantics)";
    }

    @Override
    protected void replaceMethodCall(@NotNull PsiMethodCallExpression methodCall,
                                     @NotNull PsiMethodCallExpression qualifierCall,
                                     @Nullable PsiExpression qualifierExpression) {
      if (qualifierExpression != null) {
        final PsiElement nameElement = methodCall.getMethodExpression().getReferenceNameElement();
        if (nameElement != null) {
          qualifierCall.replace(qualifierExpression);
          final Project project = methodCall.getProject();
          PsiIdentifier forEachIdentifier = JavaPsiFacade.getElementFactory(project).createIdentifier(FOR_EACH_METHOD);
          nameElement.replace(forEachIdentifier);
        }
      }
    }
  }
}
