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
package com.intellij.refactoring.typeMigration.inspections;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.quickfix.VariableTypeFix;
import com.intellij.codeInspection.*;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.typeMigration.*;
import com.intellij.refactoring.typeMigration.rules.TypeConversionRule;
import com.intellij.refactoring.typeMigration.rules.guava.BaseGuavaTypeConversionRule;
import com.intellij.refactoring.typeMigration.rules.guava.GuavaFluentIterableConversionRule;
import com.intellij.refactoring.typeMigration.rules.guava.GuavaFunctionConversionRule;
import com.intellij.refactoring.typeMigration.rules.guava.GuavaOptionalConversionRule;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
@SuppressWarnings("DialogTitleCapitalization")
public class GuavaInspection extends BaseJavaLocalInspectionTool {
  private final static Logger LOG = Logger.getInstance(GuavaInspection.class);

  private final static String PROBLEM_DESCRIPTION_FOR_VARIABLE = "Guava's functional primitives can be replaced by Java API";
  private final static String PROBLEM_DESCRIPTION_FOR_METHOD_CHAIN = "Guava's FluentIterable method chain can be replaced by Java API";

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      private final AtomicNotNullLazyValue<Map<String, PsiClass>> myGuavaClassConversions =
        new AtomicNotNullLazyValue<Map<String, PsiClass>>() {
          @NotNull
          @Override
          protected Map<String, PsiClass> compute() {
            Map<String, PsiClass> map = new HashMap<String, PsiClass>();
            for (TypeConversionRule rule : TypeConversionRule.EP_NAME.getExtensions()) {
              if (rule instanceof BaseGuavaTypeConversionRule) {
                final String fromClass = ((BaseGuavaTypeConversionRule)rule).ruleFromClass();
                final String toClass = ((BaseGuavaTypeConversionRule)rule).ruleToClass();

                final Project project = holder.getProject();
                final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
                final PsiClass targetClass = javaPsiFacade.findClass(toClass, GlobalSearchScope.allScope(project));

                if (targetClass != null) {
                  map.put(fromClass, targetClass);
                }

              }
            }
            return map;
          }
        };

      @Override
      public void visitVariable(PsiVariable variable) {
        final PsiType type = variable.getType();
        if (type instanceof PsiClassType) {
          final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
          final PsiClass psiClass = resolveResult.getElement();
          if (psiClass != null) {
            final String qName = psiClass.getQualifiedName();
            final PsiClass targetClass = myGuavaClassConversions.getValue().get(qName);
            if (targetClass != null) {
              final VariableTypeFix fix = TypeMigrationVariableTypeFixProvider.createTypeMigrationFix(variable, addTypeParameters(type, resolveResult, targetClass));
              holder.registerProblem(variable, PROBLEM_DESCRIPTION_FOR_VARIABLE, fix);
            }
          }
        }
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        if (!isFluentIterableFromCall(expression)) return;

        final PsiMethodCallExpression chain = findGuavaMethodChain(expression);

        final PsiElement maybeLocalVariable = chain.getParent();
        if (maybeLocalVariable instanceof PsiLocalVariable) {
          final PsiClass aClass = PsiUtil.resolveClassInType(chain.getType());
          if (aClass != null && GuavaFluentIterableConversionRule.FLUENT_ITERABLE.equals(aClass.getQualifiedName())) {
            return;
          }
        }

        PsiClassType initialType = (PsiClassType)expression.getType();
        LOG.assertTrue(initialType != null);
        PsiClass resolvedClass = initialType.resolve();
        PsiClass target;
        if (resolvedClass == null || (target = myGuavaClassConversions.getValue().get(resolvedClass.getQualifiedName())) == null) {
          return;
        }
        PsiClassType targetType = addTypeParameters(initialType, initialType.resolveGenerics(), target);

        holder.registerProblem(chain, PROBLEM_DESCRIPTION_FOR_METHOD_CHAIN, new MigrateFluentIterableChainQuickFix(chain, initialType, targetType));
      }

      private boolean isFluentIterableFromCall(PsiMethodCallExpression expression) {
        PsiMethod method = expression.resolveMethod();
        if (method == null || !"from".equals(method.getName())) {
          return false;
        }
        PsiClass aClass = method.getContainingClass();
        return aClass != null && GuavaFluentIterableConversionRule.FLUENT_ITERABLE.equals(aClass.getQualifiedName());
      }

      private PsiMethodCallExpression findGuavaMethodChain(PsiMethodCallExpression expression) {
        PsiMethodCallExpression chain = expression;
        while (true) {
          final PsiMethodCallExpression current = PsiTreeUtil.getParentOfType(chain, PsiMethodCallExpression.class);
          if (current != null && current.getMethodExpression().getQualifierExpression() == chain) {
            final PsiMethod method = current.resolveMethod();
            if (method == null) {
              return chain;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null || ! (GuavaFluentIterableConversionRule.FLUENT_ITERABLE.equals(containingClass.getQualifiedName())
                                              || GuavaOptionalConversionRule.GUAVA_OPTIONAL.equals(containingClass.getQualifiedName()))) {
              return chain;
            }
          } else {
            return chain;
          }
          chain = current;
        }
      }

      private PsiClassType addTypeParameters(PsiType currentType, PsiClassType.ClassResolveResult currentTypeResolveResult, PsiClass targetClass) {
        final Map<PsiTypeParameter, PsiType> substitutionMap = currentTypeResolveResult.getSubstitutor().getSubstitutionMap();
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(holder.getProject());
        if (substitutionMap.size() == 1) {
          return elementFactory.createType(targetClass, ContainerUtil.getFirstItem(substitutionMap.values()));
        } else {
          LOG.assertTrue(substitutionMap.size() == 2);
          LOG.assertTrue(GuavaFunctionConversionRule.JAVA_UTIL_FUNCTION_FUNCTION.equals(targetClass.getQualifiedName()));
          final PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(currentType);
          final List<PsiType> types = new ArrayList<PsiType>(substitutionMap.values());
          types.remove(returnType);
          final PsiType parameterType = types.get(0);
          return elementFactory.createType(targetClass, parameterType, returnType);
        }
      }
    };
  }

  public static class MigrateFluentIterableChainQuickFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    private final PsiClassType myInitialType;
    private final PsiClassType myTargetType;

    private MigrateFluentIterableChainQuickFix(@Nullable PsiElement element, PsiClassType initialType, PsiClassType targetType) {
      super(element);
      myInitialType = initialType;
      myTargetType = targetType;
    }

    @Override
    public void invoke(@NotNull Project project,
                       @NotNull PsiFile file,
                       @Nullable("is null when called from inspection") Editor editor,
                       @NotNull PsiElement startElement,
                       @NotNull PsiElement endElement) {

      if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
      try {
        final PsiMethodCallExpression expr = (PsiMethodCallExpression)startElement;
        final TypeMigrationRules rules = new TypeMigrationRules(myInitialType);
        rules.setMigrationRootType(myTargetType);
        rules.setBoundScope(GlobalSearchScope.fileScope(file));
        final TypeConversionDescriptorBase conversion =
          rules.findConversion(myInitialType, myTargetType, expr.resolveMethod(), expr, new TypeMigrationLabeler(rules));
        LOG.assertTrue(conversion != null);
        final PsiElement replacedExpression = TypeMigrationReplacementUtil.replaceExpression(expr, project, conversion);
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(replacedExpression);
        UndoUtil.markPsiFileForUndo(file);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    @Override
    public boolean startInWriteAction() {
      return true;
    }

    @NotNull
    @Override
    public String getText() {
      return getFamilyName();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace FluentIterable method chain by Java API";
    }
  }
}