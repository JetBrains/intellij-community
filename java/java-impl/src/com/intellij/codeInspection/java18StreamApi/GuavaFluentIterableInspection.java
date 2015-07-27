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

import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.impl.scopes.JdkScope;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectIdentityHashingStrategy;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
@SuppressWarnings("DialogTitleCapitalization")
public class GuavaFluentIterableInspection extends BaseJavaBatchLocalInspectionTool {
  private final static Logger LOG = Logger.getInstance(GuavaFluentIterableInspection.class);
  private final static String PROBLEM_DESCRIPTION = "FluentIterable is used while Stream API is accessible";
  public final static String GUAVA_FLUENT_ITERABLE = "com.google.common.collect.FluentIterable";
  public final static String GUAVA_OPTIONAL = "com.google.common.base.Optional";
  public final static String GUAVA_IMMUTABLE_MAP = "com.google.common.collect.ImmutableMap";
  public final static String FLUENT_ITERABLE_FROM = "from";

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    final PsiClass fluentIterable = JavaPsiFacade.getInstance(holder.getProject())
      .findClass(GUAVA_FLUENT_ITERABLE, GlobalSearchScope.allScope(holder.getProject()));
    if (fluentIterable == null) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      private final SmartPointerManager mySmartPointerManager = SmartPointerManager.getInstance(holder.getProject());
      private final Set<PsiMethodCallExpression> myMethodCallsToIgnore =
        new THashSet<PsiMethodCallExpression>(new TObjectIdentityHashingStrategy<PsiMethodCallExpression>());

      private final MultiMap<PsiLocalVariable, PsiExpression> myLocalVariablesUsages = new MultiMap<PsiLocalVariable, PsiExpression>();
      private final Set<PsiLocalVariable> myUnconvertibleVariables = new THashSet<PsiLocalVariable>();

      @Override
      public void visitLocalVariable(final PsiLocalVariable localVariable) {
        final PsiType type = localVariable.getType();
        if (!(type instanceof PsiClassType) || myUnconvertibleVariables.contains(localVariable)) {
          return;
        }
        final PsiClass variableClass = ((PsiClassType)type).resolve();
        if (variableClass == null) {
          return;
        }
        final String qualifiedName = variableClass.getQualifiedName();
        if (!GUAVA_FLUENT_ITERABLE.equals(qualifiedName)) {
          return;
        }
        final PsiCodeBlock context = PsiTreeUtil.getParentOfType(localVariable, PsiCodeBlock.class);
        if (context == null || !checkDeclaration(localVariable.getInitializer())) {
          myUnconvertibleVariables.add(localVariable);
        }
      }

      private boolean checkDeclaration(PsiExpression declaration) {
        if (declaration == null) {
          return true;
        }
        if (!(declaration instanceof PsiMethodCallExpression)) {
          return false;
        }

        PsiMethodCallExpression currentCallExpression = (PsiMethodCallExpression)declaration;
        while (true) {
          final PsiExpression qualifier = currentCallExpression.getMethodExpression().getQualifierExpression();
          if (qualifier instanceof PsiMethodCallExpression) {
            final PsiMethod method = currentCallExpression.resolveMethod();
            if (method == null || GuavaFluentIterableMethodConverters.isStopMethod(method.getName())) {
              return false;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null || !aClass.isEquivalentTo(fluentIterable)) {
              return false;
            }
            currentCallExpression = (PsiMethodCallExpression)qualifier;
          } else {
            if (qualifier instanceof PsiReferenceExpression) {
              final PsiMethod method = currentCallExpression.resolveMethod();
              if (method == null || !FLUENT_ITERABLE_FROM.equals(method.getName())) {
                return false;
              }
              final PsiClass aClass = method.getContainingClass();
              if (aClass == null || !GUAVA_FLUENT_ITERABLE.equals(aClass.getQualifiedName())) {
                return false;
              }
              break;

            } else {
              return false;
            }
          }
        }
        return true;
      }

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement resolvedElement = expression.resolve();
        if (resolvedElement instanceof PsiLocalVariable) {
          PsiLocalVariable fluentIterableVariable = (PsiLocalVariable)resolvedElement;
          if (!fluentIterable.isEquivalentTo(PsiTypesUtil.getPsiClass(fluentIterableVariable.getType())) ||
              myUnconvertibleVariables.contains(fluentIterableVariable)) {
            return;
          }
          analyzeExpression(expression, fluentIterableVariable);
        }
      }

      private void addToUnconvertible(PsiLocalVariable variable) {
        myUnconvertibleVariables.add(variable);
        myLocalVariablesUsages.remove(variable);
      }

      @Override
      public void visitJavaFile(PsiJavaFile file) {
        super.visitJavaFile(file);
        for (Map.Entry<PsiLocalVariable, Collection<PsiExpression>> e : myLocalVariablesUsages.entrySet()) {
          final PsiLocalVariable localVariable = e.getKey();
          final Collection<PsiExpression> foundUsages = e.getValue();
          final SmartPsiElementPointer<PsiLocalVariable> variablePointer = mySmartPointerManager.createSmartPsiElementPointer(localVariable);
          final ConvertGuavaFluentIterableQuickFix quickFix = new ConvertGuavaFluentIterableQuickFix(variablePointer, ContainerUtil.map(
            new THashSet<PsiExpression>(foundUsages), new Function<PsiExpression, SmartPsiElementPointer<PsiExpression>>() {
              @Override
              public SmartPsiElementPointer<PsiExpression> fun(PsiExpression expression) {
                return mySmartPointerManager.createSmartPsiElementPointer(expression);
              }
            }));
          holder.registerProblem(localVariable, PROBLEM_DESCRIPTION, quickFix);
          for (PsiExpression usage : foundUsages) {
            holder.registerProblem(usage, PROBLEM_DESCRIPTION, quickFix);
          }
        }
        myLocalVariablesUsages.clear();
        myUnconvertibleVariables.clear();
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        if (!myMethodCallsToIgnore.add(expression)) {
          return;
        }
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        final String methodName = methodExpression.getReferenceName();
        if (FLUENT_ITERABLE_FROM.equals(methodName)) {
          final PsiMethod method = expression.resolveMethod();
          if (method == null || !method.hasModifierProperty(PsiModifier.STATIC) || !fluentIterable.isEquivalentTo(method.getContainingClass())) {
            return;
          }
          PsiMethodCallExpression currentExpression = expression;
          while (true) {
            myMethodCallsToIgnore.add(currentExpression);
            PsiMethodCallExpression parentMethodCall = PsiTreeUtil.getParentOfType(currentExpression, PsiMethodCallExpression.class);
            if (parentMethodCall != null) {
              if (parentMethodCall.getMethodExpression().getQualifierExpression() == currentExpression) {
                final PsiMethod parentCallMethod = parentMethodCall.resolveMethod();
                if (parentCallMethod != null && fluentIterable.isEquivalentTo(parentCallMethod.getContainingClass())) {
                  if (GuavaFluentIterableMethodConverters.isStopMethod(parentCallMethod.getName())) {
                    return;
                  }
                  currentExpression = parentMethodCall;
                  continue;
                }
              }
            }
            final PsiElement expressionParent = currentExpression.getParent();
            if (expressionParent instanceof PsiReturnStatement) {
              final PsiType containingMethodReturnType = findContainingMethodReturnType(currentExpression);
              if (containingMethodReturnType instanceof PsiClassType) {
                final PsiClass resolvedClass = ((PsiClassType)containingMethodReturnType).resolve();
                if (resolvedClass == null || !(resolvedClass.getResolveScope() instanceof JdkScope)) {
                  return;
                }
              }
            } else if (expressionParent instanceof PsiLocalVariable) {
              final PsiType type = ((PsiLocalVariable)expressionParent).getType();
              if (type instanceof PsiClassType) {
                final PsiClass resolvedClass = ((PsiClassType)type).resolve();
                if (resolvedClass == null || !(resolvedClass.getResolveScope() instanceof JdkScope)) {
                  return;
                }
              }
            } else if (expressionParent instanceof PsiExpressionList) {
              if (expressionParent.getParent() instanceof PsiMethodCallExpression
                && !isMethodWithParamAcceptsConversion((PsiMethodCallExpression)expressionParent.getParent(),
                                                       currentExpression,
                                                       fluentIterable)) {
                return;
              }
            }

            final List<SmartPsiElementPointer<PsiExpression>> exprAsList =
              ContainerUtil.list(mySmartPointerManager.createSmartPsiElementPointer((PsiExpression)currentExpression));
            holder.registerProblem(currentExpression, PROBLEM_DESCRIPTION, new ConvertGuavaFluentIterableQuickFix(null, exprAsList));
            return;
          }
        } else {
          final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
          if (GuavaFluentIterableMethodConverters.isFluentIterableMethod(methodName) &&
              qualifierExpression instanceof PsiReferenceExpression) {
            final PsiElement resolvedElement = ((PsiReferenceExpression)qualifierExpression).resolve();
            if (resolvedElement instanceof PsiLocalVariable) {
              PsiLocalVariable fluentIterableLocalVariable = (PsiLocalVariable)resolvedElement;
              if (!fluentIterable.isEquivalentTo(PsiTypesUtil.getPsiClass(fluentIterableLocalVariable.getType())) ||
                  myUnconvertibleVariables.contains(fluentIterableLocalVariable)) {
                return;
              }
              analyzeExpression(expression, fluentIterableLocalVariable);
            }
          }
        }
      }

      private void analyzeExpression(PsiExpression expression, PsiLocalVariable fluentIterableLocalVariable) {
        PsiExpression baseExpression = expression;
        while (true) {
          final PsiMethodCallExpression
            methodCallExpression = PsiTreeUtil.getParentOfType(baseExpression, PsiMethodCallExpression.class);
          if (methodCallExpression != null && methodCallExpression.getMethodExpression().getQualifierExpression() == baseExpression) {
            final String currentMethodName = methodCallExpression.getMethodExpression().getReferenceName();
            if (GuavaFluentIterableMethodConverters.isFluentIterableMethod(currentMethodName)) {
              if (GuavaFluentIterableMethodConverters.isStopMethod((currentMethodName))) {
                addToUnconvertible(fluentIterableLocalVariable);
                return;
              }
              else {
                final PsiMethod method = methodCallExpression.resolveMethod();
                if (method != null && method.getContainingClass() != null && method.getContainingClass().isEquivalentTo(fluentIterable)) {
                  baseExpression = methodCallExpression;
                  myMethodCallsToIgnore.add(methodCallExpression);
                  continue;
                }
              }
            }
          }
          break;
        }
        final PsiElement parent = baseExpression.getParent();
        if (parent instanceof PsiExpressionList) {
          myLocalVariablesUsages.putValue(fluentIterableLocalVariable, baseExpression);
          final boolean suitable = parent.getParent() instanceof PsiMethodCallExpression &&
                            isMethodWithParamAcceptsConversion((PsiMethodCallExpression)parent.getParent(), baseExpression,
                                                               fluentIterable);
          if (!suitable) {
            addToUnconvertible(fluentIterableLocalVariable);
          }
        } else if (parent instanceof PsiReferenceExpression) {
          final PsiMethodCallExpression parentMethodCall = PsiTreeUtil.getParentOfType(baseExpression, PsiMethodCallExpression.class);
          if (parentMethodCall != null && parentMethodCall.getMethodExpression().getQualifier() == baseExpression) {
            if (GuavaOptionalConverter.isConvertibleIfOption(parentMethodCall)) {
              myLocalVariablesUsages.putValue(fluentIterableLocalVariable, baseExpression);
            } else {
              addToUnconvertible(fluentIterableLocalVariable);
            }
          }
        }
        else if (parent instanceof PsiLocalVariable) {
          myLocalVariablesUsages.putValue(fluentIterableLocalVariable, baseExpression);
        } else if (parent instanceof PsiAssignmentExpression) {
          final PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
          final PsiExpression lExpression = assignment.getLExpression();
          if (lExpression instanceof PsiReferenceExpression) {
            if (((PsiReferenceExpression)lExpression).isReferenceTo(fluentIterableLocalVariable)) {
              if (isSelfAssignment(assignment, fluentIterableLocalVariable)) {
                myLocalVariablesUsages.putValue(fluentIterableLocalVariable, baseExpression);
                return;
              }
              if (checkDeclaration(assignment.getRExpression())) {
                myLocalVariablesUsages.putValue(fluentIterableLocalVariable, assignment.getRExpression());
                return;
              }
              addToUnconvertible(fluentIterableLocalVariable);
            } else {
              myLocalVariablesUsages.putValue(fluentIterableLocalVariable, baseExpression);
            }
          }
          else {
            myLocalVariablesUsages.putValue(fluentIterableLocalVariable, baseExpression);
          }
        } else if (parent instanceof PsiReturnStatement) {
          final PsiType containingMethodReturnType = findContainingMethodReturnType(baseExpression);
          if (baseExpression == expression) {
            if (!(containingMethodReturnType instanceof PsiClassType)) {
              addToUnconvertible(fluentIterableLocalVariable);
              return;
            }
            final PsiClass resolvedClass = ((PsiClassType)containingMethodReturnType).resolve();
            if (resolvedClass == null || (!CommonClassNames.JAVA_LANG_ITERABLE.equals(resolvedClass.getQualifiedName()) &&
                                          !CommonClassNames.JAVA_LANG_OBJECT.equals(resolvedClass.getQualifiedName()))) {
              addToUnconvertible(fluentIterableLocalVariable);
            } else {
              myLocalVariablesUsages.putValue(fluentIterableLocalVariable, baseExpression);
            }
          }
          else {
            if (containingMethodReturnType instanceof PsiClassType) {
              final PsiClass resolvedClass = ((PsiClassType)containingMethodReturnType).resolve();
              if (resolvedClass == null || !(resolvedClass.getResolveScope() instanceof JdkScope)) {
                addToUnconvertible(fluentIterableLocalVariable);
              }
            }
          }
        } else if (parent instanceof PsiExpressionStatement) {
          myLocalVariablesUsages.putValue(fluentIterableLocalVariable, baseExpression);
        }
      }
    };

  }

  public static boolean isMethodWithParamAcceptsConversion(PsiMethodCallExpression methodCallExpression,
                                                     PsiExpression baseExpression,
                                                     PsiClass fluentIterable) {
    final PsiExpressionList argList = methodCallExpression.getArgumentList();
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) {
      return true;
    }
    final PsiParameterList paramList = method.getParameterList();
    if (paramList.getParametersCount() != argList.getExpressions().length &&
        !(paramList.getParameters()[paramList.getParametersCount() - 1].getType() instanceof PsiEllipsisType)) {
      return false;
    }
    int index = -1;
    PsiExpression[] expressions = argList.getExpressions();
    for (int i = 0, length = expressions.length; i < length; i++) {
      if (expressions[i] == baseExpression) {
        index = i;
        break;
      }
    }
    LOG.assertTrue(index >= 0);
    PsiType parameterType;
    if (index > paramList.getParametersCount() - 1) {
      parameterType = paramList.getParameters()[paramList.getParametersCount() - 1].getType();
    } else {
      parameterType = paramList.getParameters()[index].getType();
    }
    if (parameterType instanceof PsiEllipsisType) {
      parameterType = ((PsiEllipsisType)parameterType).getComponentType();
    }
    if (parameterType instanceof PsiClassType) {
      final PsiClass resolvedParameterClass = ((PsiClassType)parameterType).resolve();
      final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(methodCallExpression.getProject());
      final GlobalSearchScope scope = GlobalSearchScope.allScope(methodCallExpression.getProject());
      final PsiClass optional = javaPsiFacade.findClass(GUAVA_OPTIONAL, scope);
      final PsiClass immutableMap = javaPsiFacade.findClass(GUAVA_IMMUTABLE_MAP, scope);
      if (resolvedParameterClass != null &&
          (InheritanceUtil.isInheritorOrSelf(resolvedParameterClass, fluentIterable, true) ||
           InheritanceUtil.isInheritorOrSelf(resolvedParameterClass, optional, true) ||
           InheritanceUtil.isInheritorOrSelf(resolvedParameterClass, immutableMap, true))){
        return false;
      }
    }
    return true;
  }

  @Nullable
  static PsiType findContainingMethodReturnType(PsiElement methodElement) {
    final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(methodElement, PsiMethod.class);
    if (containingMethod == null) {
      return null;
    }
    final PsiType containingMethodReturnType = containingMethod.getReturnType();
    if (containingMethodReturnType == null) {
      return null;
    }
    return containingMethodReturnType;
  }

  private static boolean isSelfAssignment(PsiAssignmentExpression expression, PsiLocalVariable localVariable) {
    final PsiExpression rExpression = expression.getRExpression();
    if (!(rExpression instanceof PsiMethodCallExpression)) {
      return false;
    }

    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)rExpression;
    while (true) {
      final PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
      if (qualifier instanceof PsiMethodCallExpression) {
        methodCall = (PsiMethodCallExpression)qualifier;
      } else {
        break;
      }
    }

    final PsiExpression qualifierExpression = methodCall.getMethodExpression().getQualifierExpression();
    if (qualifierExpression instanceof PsiReferenceExpression &&
        !((PsiReferenceExpression)qualifierExpression).isReferenceTo(localVariable)) {
      return false;
    }
    return true;
  }

  public static class ConvertGuavaFluentIterableQuickFix implements LocalQuickFix {
    @Nullable private final SmartPsiElementPointer<PsiLocalVariable> myVariable;
    @NotNull private final List<SmartPsiElementPointer<PsiExpression>> myFoundUsages;

    protected ConvertGuavaFluentIterableQuickFix(@Nullable SmartPsiElementPointer<PsiLocalVariable> variable,
                                                 @NotNull List<SmartPsiElementPointer<PsiExpression>> foundUsages) {
      myVariable = variable;
      myFoundUsages = foundUsages;
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
      return "Convert Guava's FluentIterable to java.util.stream.Stream";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory elementFactory = javaPsiFacade.getElementFactory();
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      for (SmartPsiElementPointer<PsiExpression> usage : myFoundUsages) {
        final PsiExpression element = usage.getElement();
        if (element != null) {
          GuavaFluentIterableMethodConverters.convert(element, elementFactory, codeStyleManager);
        }
      }
      if (myVariable != null) {
        final PsiLocalVariable element = myVariable.getElement();
        if (element != null) {
          GuavaFluentIterableMethodConverters.convert(element, elementFactory, codeStyleManager);
        }
      }
    }
  }
}
