// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.inspections;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationBundle;
import com.intellij.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.refactoring.typeMigration.TypeMigrationRules;
import com.intellij.refactoring.typeMigration.rules.TypeConversionRule;
import com.intellij.refactoring.typeMigration.rules.guava.*;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * @author Dmitry Batkovich
 */
public final class GuavaInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(GuavaInspection.class);
  private static final Set<@NonNls String> FLUENT_ITERABLE_STOP_METHODS = ContainerUtil.newHashSet("append", "cycle", "uniqueIndex", "index", "toMultiset");

  public boolean checkVariables = true;
  public boolean checkChains = true;
  public boolean checkReturnTypes = true;
  public boolean ignoreJavaxNullable = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("checkVariables", TypeMigrationBundle.message("inspection.guava.variables.option")),
      checkbox("checkChains", TypeMigrationBundle.message("inspection.guava.method.chains.option")),
      checkbox("checkReturnTypes", TypeMigrationBundle.message("inspection.guava.return.types.option")),
      checkbox("ignoreJavaxNullable", TypeMigrationBundle.message("inspection.guava.erase.option")));
  }

    @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.STREAM_OPTIONAL);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      private final NotNullLazyValue<Map<String, PsiClass>> myGuavaClassConversions = NotNullLazyValue.atomicLazy(() -> {
        Map<String, PsiClass> map = new HashMap<>();
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
      });

      @Override
      public void visitVariable(@NotNull PsiVariable variable) {
        if (!checkVariables) return;
        final PsiIdentifier identifier = variable.getNameIdentifier();
        if (identifier == null) return;
        final PsiType type = variable.getType();
        PsiType targetType = getConversionClassType(type);
        if (targetType != null) {
          holder.registerProblem(identifier,
                                 TypeMigrationBundle.message("guava.functional.primitives.can.be.replaced.by.java.api.problem.description"),
                                 new MigrateGuavaTypeFix(variable, targetType));
        }
      }

      @Override
      public void visitMethod(@NotNull PsiMethod method) {
        super.visitMethod(method);
        if (!checkReturnTypes) return;
        final PsiType targetType = getConversionClassType(method.getReturnType());
        if (targetType != null) {
          final PsiTypeElement typeElement = method.getReturnTypeElement();
          if (typeElement != null) {
            holder.registerProblem(typeElement,
                                   TypeMigrationBundle.message("guava.functional.primitives.can.be.replaced.by.java.api.problem.description"),
                                   new MigrateGuavaTypeFix(method, targetType));
          }
        }
      }

      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        checkFluentIterableGenerationMethod(expression);
        checkPredicatesUtilityMethod(expression);
      }

      private void checkPredicatesUtilityMethod(@NotNull PsiMethodCallExpression expression) {
        if (GuavaPredicateConversionRule.isPredicates(expression) && isPossibleToConvertPredicate(expression)) {
          final PsiClassType initialType = (PsiClassType)expression.getType();
          if (initialType == null) return;
          PsiClassType targetType = createTargetType(initialType);
          if (targetType == null) return;
          final PsiElement anchor = expression.getMethodExpression().getReferenceNameElement();
          assert anchor != null;
          holder.registerProblem(anchor,
                                 TypeMigrationBundle.message("guava.functional.primitives.can.be.replaced.by.java.api.problem.description"),
                                 new MigrateGuavaTypeFix(expression, targetType));
        }
      }

      private static boolean isPossibleToConvertPredicate(@NotNull PsiMethodCallExpression expression) {
        PsiMethodCallExpression enclosingMethodCallExpression =
          PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class);

        return enclosingMethodCallExpression == null ||
               isReceiverOfEnclosingCallAFluentIterable(enclosingMethodCallExpression) ||
               GuavaPredicateConversionRule.isEnclosingCallAPredicate(enclosingMethodCallExpression) ||
               GuavaPredicateConversionRule.isPredicateConvertibleInsideEnclosingMethod(expression, enclosingMethodCallExpression);
      }

      private void checkFluentIterableGenerationMethod(PsiMethodCallExpression expression) {
        if (!checkChains) return;
        if (!isFluentIterableFromCall(expression)) return;

        final PsiMethodCallExpression chain = findGuavaMethodChain(expression);
        if (chain == null) {
          return;
        }

        PsiClassType initialType = (PsiClassType)chain.getType();
        LOG.assertTrue(initialType != null);
        PsiClassType targetType = createTargetType(initialType);
        if (targetType == null) return;

        PsiElement highlightedElement = chain;
        if (chain.getParent() instanceof PsiReferenceExpression && chain.getParent().getParent() instanceof PsiMethodCallExpression) {
          highlightedElement = chain.getParent().getParent();
        }
        holder.registerProblem(highlightedElement,
                               TypeMigrationBundle.message("guava.functional.primitives.can.be.replaced.by.java.api.problem.description"), new MigrateGuavaTypeFix(chain, targetType));
      }

      private @Nullable PsiClassType createTargetType(PsiClassType initialType) {
        PsiClass resolvedClass = initialType.resolve();
        PsiClass target;
        if (resolvedClass == null || (target = myGuavaClassConversions.getValue().get(resolvedClass.getQualifiedName())) == null) {
          return null;
        }
        return addTypeParameters(initialType, initialType.resolveGenerics(), target);
      }

      private PsiType getConversionClassType(PsiType initialType) {
        if (initialType == null) return null;
        final PsiType type = initialType.getDeepComponentType();
        if (type instanceof PsiClassType) {
          final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
          final PsiClass psiClass = resolveResult.getElement();
          if (psiClass != null) {
            final String qName = psiClass.getQualifiedName();
            final PsiClass targetClass = myGuavaClassConversions.getValue().get(qName);
            if (targetClass != null) {
              final PsiClassType createdType = addTypeParameters(type, resolveResult, targetClass);
              return initialType instanceof PsiArrayType ? wrapAsArray((PsiArrayType)initialType, createdType) : createdType;
            }
          }
        }
        return null;
      }

      private static PsiType wrapAsArray(PsiArrayType initial, PsiType created) {
        PsiArrayType result = new PsiArrayType(created);
        while (initial.getComponentType() instanceof PsiArrayType) {
          initial = (PsiArrayType)initial.getComponentType();
          result = new PsiArrayType(result);
        }
        return result;
      }

      public static boolean isReceiverOfEnclosingCallAFluentIterable(@NotNull PsiMethodCallExpression enclosingMethodCallExpression) {
        PsiMethod method = enclosingMethodCallExpression.resolveMethod();
        if (method == null) return false;
        return isFluentIterable(method.getContainingClass());
      }

      private static boolean isFluentIterableFromCall(PsiMethodCallExpression expression) {
        PsiMethod method = expression.resolveMethod();
        if (method == null || !GuavaFluentIterableConversionRule.CHAIN_HEAD_METHODS.contains(method.getName())) {
          return false;
        }
        PsiClass aClass = method.getContainingClass();
        return isFluentIterable(aClass);
      }

      private static boolean isFluentIterable(PsiClass aClass) {
        return aClass != null && (GuavaOptionalConversionRule.GUAVA_OPTIONAL.equals(aClass.getQualifiedName()) ||
                                  GuavaFluentIterableConversionRule.FLUENT_ITERABLE.equals(aClass.getQualifiedName()));
      }

      private static PsiMethodCallExpression findGuavaMethodChain(PsiMethodCallExpression expression) {
        PsiMethodCallExpression chain = expression;
        while (true) {
          final PsiMethodCallExpression current = PsiTreeUtil.getParentOfType(chain, PsiMethodCallExpression.class);
          if (current != null && current.getMethodExpression().getQualifierExpression() == chain) {
            final PsiMethod method = current.resolveMethod();
            if (method == null) {
              return chain;
            }
            if (FLUENT_ITERABLE_STOP_METHODS.contains(method.getName())) {
              return null;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null || !(GuavaFluentIterableConversionRule.FLUENT_ITERABLE.equals(containingClass.getQualifiedName())
                                             || GuavaOptionalConversionRule.GUAVA_OPTIONAL.equals(containingClass.getQualifiedName()))) {
              return chain;
            }
            final PsiType returnType = method.getReturnType();
            final PsiClass returnClass = PsiTypesUtil.getPsiClass(returnType);
            if (returnClass == null || !(GuavaFluentIterableConversionRule.FLUENT_ITERABLE.equals(returnClass.getQualifiedName())
                                    || GuavaOptionalConversionRule.GUAVA_OPTIONAL.equals(returnClass.getQualifiedName()))) {
              return chain;
            }
            if (GuavaTypeConversionDescriptor.isIterable(current)) {
              return chain;
            }
          }
          else {
            return chain;
          }
          chain = current;
        }
      }

      private @NotNull PsiClassType addTypeParameters(PsiType currentType,
                                                      PsiClassType.ClassResolveResult currentTypeResolveResult,
                                                      PsiClass targetClass) {
        final Map<PsiTypeParameter, PsiType> substitutionMap = currentTypeResolveResult.getSubstitutor().getSubstitutionMap();
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(holder.getProject());
        if (substitutionMap.size() == 1) {
          return elementFactory.createType(targetClass, ContainerUtil.getFirstItem(substitutionMap.values()));
        }
        else {
          LOG.assertTrue(substitutionMap.size() == 2);
          LOG.assertTrue(GuavaLambda.FUNCTION.getJavaAnalogueClassQName().equals(targetClass.getQualifiedName()));
          final PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(currentType);
          final List<PsiType> types = new ArrayList<>(substitutionMap.values());
          types.remove(returnType);
          final PsiType parameterType = types.get(0);
          return elementFactory.createType(targetClass, parameterType, returnType);
        }
      }
    };
  }

  public final class MigrateGuavaTypeFix extends LocalQuickFixAndIntentionActionOnPsiElement implements BatchQuickFix {
    private final PsiType myTargetType;

    private MigrateGuavaTypeFix(@NotNull PsiElement element, PsiType targetType) {
      super(element);
      myTargetType = targetType;
    }

    @Override
    public void invoke(@NotNull Project project,
                       @NotNull PsiFile psiFile,
                       @Nullable Editor editor,
                       @NotNull PsiElement startElement,
                       @NotNull PsiElement endElement) {
      performTypeMigration(Collections.singletonList(startElement), Collections.singletonList(myTargetType));
    }

    @Override
    protected boolean isAvailable() {
      return super.isAvailable() && myTargetType.isValid();
    }

    @Override
    public @NotNull String getText() {
      final PsiElement element = getStartElement();
      if (!myTargetType.isValid() || !element.isValid()) {
        return getFamilyName();
      }
      if (element instanceof PsiMethodCallExpression) {
        return TypeMigrationBundle.message("migrate.method.chain.fix.text", myTargetType.getCanonicalText(false));
      }

      return TypeMigrationBundle.message("migrate.fix.text",
                                         TypeMigrationProcessor.getPresentation(element),
                                         StringUtil.escapeXmlEntities(myTargetType.getCanonicalText(false)));
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return TypeMigrationBundle.message("migrate.guava.to.java.family.name");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(final @NotNull Project project,
                         CommonProblemDescriptor @NotNull [] descriptors,
                         @NotNull List<PsiElement> psiElementsToIgnore,
                         @Nullable Runnable refreshViews) {
      final List<PsiElement> elementsToFix = new ArrayList<>();
      final List<PsiType> migrationTypes = new ArrayList<>();

      for (CommonProblemDescriptor descriptor : descriptors) {
        final MigrateGuavaTypeFix fix = getFix(descriptor);
        elementsToFix.add(fix.getStartElement());
        migrationTypes.add(fix.myTargetType);
      }

      if (!elementsToFix.isEmpty()) performTypeMigration(elementsToFix, migrationTypes);
    }

    private MigrateGuavaTypeFix getFix(CommonProblemDescriptor descriptor) {
      final QuickFix<?>[] fixes = descriptor.getFixes();
      LOG.assertTrue(fixes != null);
      for (QuickFix<?> fix : fixes) {
        if (fix instanceof MigrateGuavaTypeFix) {
          return (MigrateGuavaTypeFix)fix;
        }
      }
      throw new AssertionError();
    }

    private void performTypeMigration(List<? extends PsiElement> elements, List<PsiType> types) {
      if (!FileModificationService.getInstance().preparePsiElementsForWrite(elements)) return;
      final Project project = elements.get(0).getProject();
      final TypeMigrationRules rules = new TypeMigrationRules(project);
      rules.setBoundScope(GlobalSearchScopesCore.projectProductionScope(project)
                            .union(GlobalSearchScopesCore.projectTestScope(project)));
      rules.addConversionRuleSettings(new GuavaConversionSettings(ignoreJavaxNullable));
      TypeMigrationProcessor.runHighlightingTypeMigration(project,
                                                          null,
                                                          rules,
                                                          elements.toArray(PsiElement.EMPTY_ARRAY),
                                                          createMigrationTypeFunction(elements, types),
                                                          true,
                                                          true);
    }

    private static Function<PsiElement, PsiType> createMigrationTypeFunction(final @NotNull List<? extends PsiElement> elements,
                                                                             final @NotNull List<PsiType> types) {
      LOG.assertTrue(elements.size() == types.size());
      final Map<PsiElement, PsiType> mappings = new HashMap<>();
      final Iterator<PsiType> typeIterator = types.iterator();
      for (PsiElement element : elements) {
        PsiType type = typeIterator.next();
        mappings.put(element, type);
      }
      return element -> mappings.get(element);
    }
  }
}