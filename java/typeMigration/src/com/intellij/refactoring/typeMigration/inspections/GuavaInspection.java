// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.typeMigration.inspections;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public class GuavaInspection extends AbstractBaseJavaLocalInspectionTool {
  private final static Logger LOG = Logger.getInstance(GuavaInspection.class);
  private final static Set<String> FLUENT_ITERABLE_STOP_METHODS = ContainerUtil.newHashSet("append", "cycle", "uniqueIndex", "index", "toMultiset");

  public final static String PROBLEM_DESCRIPTION = "Guava's functional primitives can be replaced by Java API";


  public boolean checkVariables = true;
  public boolean checkChains = true;
  public boolean checkReturnTypes = true;
  public boolean ignoreJavaxNullable = true;

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(TypeMigrationBundle.message("inspection.guava.variables.option"), "checkVariables");
    panel.addCheckbox(TypeMigrationBundle.message("inspection.guava.method.chains.option"), "checkChains");
    panel.addCheckbox(TypeMigrationBundle.message("inspection.guava.return.types.option"), "checkReturnTypes");
    panel.addCheckbox(TypeMigrationBundle.message("inspection.guava.erase.option"), "ignoreJavaxNullable");
    return panel;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!JavaFeature.STREAMS.isFeatureSupported(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      private final AtomicNotNullLazyValue<Map<String, PsiClass>> myGuavaClassConversions =
        new AtomicNotNullLazyValue<Map<String, PsiClass>>() {
          @NotNull
          @Override
          protected Map<String, PsiClass> compute() {
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
          }
        };

      @Override
      public void visitVariable(PsiVariable variable) {
        if (!checkVariables) return;
        final PsiType type = variable.getType();
        PsiType targetType = getConversionClassType(type);
        if (targetType != null) {
          holder.registerProblem(variable.getNameIdentifier(),
                                 PROBLEM_DESCRIPTION,
                                 new MigrateGuavaTypeFix(variable, targetType));
        }
      }

      @Override
      public void visitMethod(PsiMethod method) {
        super.visitMethod(method);
        if (!checkReturnTypes) return;
        final PsiType targetType = getConversionClassType(method.getReturnType());
        if (targetType != null) {
          final PsiTypeElement typeElement = method.getReturnTypeElement();
          if (typeElement != null) {
            holder.registerProblem(typeElement,
                                   PROBLEM_DESCRIPTION,
                                   new MigrateGuavaTypeFix(method, targetType));
          }
        }
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        checkFluentIterableGenerationMethod(expression);
        checkPredicatesUtilityMethod(expression);
      }

      private void checkPredicatesUtilityMethod(PsiMethodCallExpression expression) {
        if (GuavaPredicateConversionRule.isPredicates(expression)) {
          final PsiClassType initialType = (PsiClassType)expression.getType();
          PsiClassType targetType = createTargetType(initialType);
          if (targetType == null) return;
          holder.registerProblem(expression.getMethodExpression().getReferenceNameElement(),
                                 PROBLEM_DESCRIPTION,
                                 new MigrateGuavaTypeFix(expression, targetType));
        }
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
        holder.registerProblem(highlightedElement, PROBLEM_DESCRIPTION, new MigrateGuavaTypeFix(chain, targetType));
      }

      @Nullable
      private PsiClassType createTargetType(PsiClassType initialType) {
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

      private PsiType wrapAsArray(PsiArrayType initial, PsiType created) {
        PsiArrayType result = new PsiArrayType(created);
        while (initial.getComponentType() instanceof PsiArrayType) {
          initial = (PsiArrayType)initial.getComponentType();
          result = new PsiArrayType(result);
        }
        return result;
      }

      private boolean isFluentIterableFromCall(PsiMethodCallExpression expression) {
        PsiMethod method = expression.resolveMethod();
        if (method == null || !GuavaFluentIterableConversionRule.CHAIN_HEAD_METHODS.contains(method.getName())) {
          return false;
        }
        PsiClass aClass = method.getContainingClass();
        return aClass != null && (GuavaOptionalConversionRule.GUAVA_OPTIONAL.equals(aClass.getQualifiedName()) ||
                                  GuavaFluentIterableConversionRule.FLUENT_ITERABLE.equals(aClass.getQualifiedName()));
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

      @NotNull
      private PsiClassType addTypeParameters(PsiType currentType,
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

  public final class MigrateGuavaTypeFix extends LocalQuickFixAndIntentionActionOnPsiElement implements BatchQuickFix<CommonProblemDescriptor> {
    public static final String FAMILY_NAME = "Migrate Guava's type to Java";
    private final PsiType myTargetType;

    private MigrateGuavaTypeFix(@NotNull PsiElement element, PsiType targetType) {
      super(element);
      myTargetType = targetType;
    }

    @Override
    public void invoke(@NotNull Project project,
                       @NotNull PsiFile file,
                       @Nullable Editor editor,
                       @NotNull PsiElement startElement,
                       @NotNull PsiElement endElement) {
      performTypeMigration(Collections.singletonList(startElement), Collections.singletonList(myTargetType));
    }

    @Override
    protected boolean isAvailable() {
      return super.isAvailable() && myTargetType.isValid();
    }

    @NotNull
    @Override
    public String getText() {
      final PsiElement element = getStartElement();
      if (!myTargetType.isValid() || !element.isValid()) {
        return getFamilyName();
      }
      final String presentation;
      if (element instanceof PsiMethodCallExpression) {
        presentation = "method chain";
      }
      else {
        presentation = TypeMigrationProcessor.getPresentation(element);
      }
      return TypeMigrationBundle.message("migrate.fix.text", presentation, myTargetType.getCanonicalText(false));
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return FAMILY_NAME;
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull final Project project,
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
      final QuickFix[] fixes = descriptor.getFixes();
      LOG.assertTrue(fixes != null);
      for (QuickFix fix : fixes) {
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

    private Function<PsiElement, PsiType> createMigrationTypeFunction(@NotNull final List<? extends PsiElement> elements,
                                                                             @NotNull final List<PsiType> types) {
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