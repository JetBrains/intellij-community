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
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.typeMigration.*;
import com.intellij.refactoring.typeMigration.rules.TypeConversionRule;
import com.intellij.refactoring.typeMigration.rules.guava.BaseGuavaTypeConversionRule;
import com.intellij.refactoring.typeMigration.rules.guava.GuavaFluentIterableConversionRule;
import com.intellij.refactoring.typeMigration.rules.guava.GuavaFunctionConversionRule;
import com.intellij.refactoring.typeMigration.rules.guava.GuavaOptionalConversionRule;
import com.intellij.reference.SoftLazyValue;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author Dmitry Batkovich
 */
@SuppressWarnings("DialogTitleCapitalization")
public class GuavaInspection extends BaseJavaLocalInspectionTool {
  //public class GuavaInspection extends BaseJavaBatchLocalInspectionTool {
  private final static Logger LOG = Logger.getInstance(GuavaInspection.class);

  public final static String PROBLEM_DESCRIPTION_FOR_VARIABLE = "Guava's functional primitives can be replaced by Java API";
  public final static String PROBLEM_DESCRIPTION_FOR_METHOD_CHAIN = "Guava's FluentIterable method chain can be replaced by Java API";

  private final static SoftLazyValue<Set<String>> FLUENT_ITERABLE_STOP_METHODS = new SoftLazyValue<Set<String>>() {
    @NotNull
    @Override
    protected Set<String> compute() {
      return ContainerUtil.newHashSet("append", "cycle", "uniqueIndex", "index");
    }
  };

  public boolean checkVariables = true;
  public boolean checkChains = true;
  public boolean checkReturnTypes = true;

  @SuppressWarnings("Duplicates")
  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox("Report variables", "checkVariables");
    panel.addCheckbox("Report method chains", "checkChains");
    panel.addCheckbox("Report return types", "checkReturnTypes");
    return panel;
  }

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
        if (!checkVariables) return;
        final PsiType type = variable.getType();
        PsiType targetType = getConversionClassType(type);
        if (targetType != null) {
          holder.registerProblem(variable.getNameIdentifier(),
                                 PROBLEM_DESCRIPTION_FOR_VARIABLE,
                                 new MigrateGuavaTypeFix(variable, targetType, null));
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
                                   PROBLEM_DESCRIPTION_FOR_VARIABLE,
                                   new MigrateGuavaTypeFix(method, targetType, null));
          }
        }
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        if (!checkChains) return;
        if (!isFluentIterableFromCall(expression)) return;

        final PsiMethodCallExpression chain = findGuavaMethodChain(expression);
        if (chain == null) {
          return;
        }

        final PsiElement maybeLocalVariable = chain.getParent();
        if (maybeLocalVariable instanceof PsiLocalVariable) {
          final PsiClass aClass = PsiUtil.resolveClassInType(((PsiLocalVariable)maybeLocalVariable).getType());
          if (aClass != null && (GuavaFluentIterableConversionRule.FLUENT_ITERABLE.equals(aClass.getQualifiedName()) ||
                                 GuavaOptionalConversionRule.GUAVA_OPTIONAL.equals(aClass.getQualifiedName()))) {
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

        holder.registerProblem(chain, PROBLEM_DESCRIPTION_FOR_METHOD_CHAIN, new MigrateGuavaTypeFix(chain, targetType, initialType));
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

      ;

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
            if (FLUENT_ITERABLE_STOP_METHODS.getValue().contains(method.getName())) {
              return null;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null || !(GuavaFluentIterableConversionRule.FLUENT_ITERABLE.equals(containingClass.getQualifiedName())
                                             || GuavaOptionalConversionRule.GUAVA_OPTIONAL.equals(containingClass.getQualifiedName()))) {
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

  public static class MigrateGuavaTypeFix extends LocalQuickFixAndIntentionActionOnPsiElement implements BatchQuickFix<ProblemDescriptor> {
    @Nullable
    private final PsiType myInitialType;
    private final PsiType myTargetType;

    private MigrateGuavaTypeFix(@NotNull PsiElement element, PsiType targetType, @Nullable PsiType initialType) {
      super(element);
      myTargetType = targetType;
      myInitialType = initialType;
    }

    @Override
    public void invoke(@NotNull Project project,
                       @NotNull PsiFile file,
                       @Nullable("is null when called from inspection") Editor editor,
                       @NotNull PsiElement startElement,
                       @NotNull PsiElement endElement) {
      if (myInitialType == null) {
        performTypeMigration(Collections.singletonList(startElement), Collections.singletonList(myTargetType));
      } else {
        performChainTypeMigration(project,
                                  Collections.singletonList(startElement),
                                  Collections.singletonList(myInitialType),
                                  Collections.singletonList(myTargetType));
      }
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
      final String presentation = TypeMigrationProcessor.getPresentation(element);
      return "Migrate " + presentation + " type to '" + myTargetType.getCanonicalText(false) + "'";
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Migrate Guava's type to Java";
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull final Project project,
                         @NotNull ProblemDescriptor[] descriptors,
                         @NotNull List<PsiElement> psiElementsToIgnore,
                         @Nullable Runnable refreshViews) {
      final List<PsiElement> elementsToFix = new ArrayList<PsiElement>();
      final List<PsiType> migrationTypes = new ArrayList<PsiType>();

      final List<PsiElement> chainsToFix = new ArrayList<PsiElement>();
      final List<PsiType> chainsMigrationTypes = new ArrayList<PsiType>();
      final List<PsiType> chainsInitialTypes = new ArrayList<PsiType>();

      for (ProblemDescriptor descriptor : descriptors) {
        final MigrateGuavaTypeFix fix = getFix(descriptor);
        if (fix.myInitialType == null) {
          elementsToFix.add(fix.getStartElement());
          migrationTypes.add(fix.myTargetType);
        }
        else {
          chainsToFix.add(fix.getStartElement());
          chainsMigrationTypes.add(fix.myTargetType);
          chainsInitialTypes.add(fix.myInitialType);
        }
      }
      if(!elementsToFix.isEmpty() && !performTypeMigration(elementsToFix, migrationTypes)) return;

      if (!chainsToFix.isEmpty()) {
        performChainTypeMigration(project, chainsToFix, chainsInitialTypes, chainsMigrationTypes);
      }
    }

    private static void performChainTypeMigration(@NotNull final Project project, final List<PsiElement> elements, List<PsiType> initialTypes, List<PsiType> targetTypes) {
      final Iterator<PsiType> initialTypeIterator = initialTypes.iterator();
      final Iterator<PsiType> targetTypeIterator = targetTypes.iterator();

      final List<PsiElement> validElement = new ArrayList<PsiElement>();
      final List<Boolean> isIterableList = new ArrayList<Boolean>(elements.size());
      final List<TypeConversionDescriptorBase> conversionList = new ArrayList<TypeConversionDescriptorBase>(elements.size());

      for (PsiElement element : elements) {
        final PsiType initialType = initialTypeIterator.next();
        final PsiType targetType = targetTypeIterator.next();
        if (element.isValid()) {
          PsiMethodCallExpression expr = (PsiMethodCallExpression) element;
          final TypeMigrationRules rules = new TypeMigrationRules();
          rules.setBoundScope(element.getUseScope());
          conversionList.add(rules.findConversion(initialType, targetType, expr.resolveMethod(), expr, new TypeMigrationLabeler(rules, targetType)));
          isIterableList.add(isIterable(expr));
          validElement.add(element);
        }
      }

      if (!validElement.isEmpty()) {
        final PsiFile file = validElement.get(0).getContainingFile();
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;

        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            final Iterator<Boolean> isIterableIterator = isIterableList.iterator();
            final Iterator<TypeConversionDescriptorBase> conversionIterator = conversionList.iterator();
            for (PsiElement element : validElement) {
              PsiElement replacedExpression = TypeMigrationReplacementUtil.replaceExpression((PsiExpression)element, project, conversionIterator.next());
              if (isIterableIterator.next()) {
                final String expressionText = replacedExpression.getText() + ".collect(java.util.stream.Collectors.toList())";
                replacedExpression = replacedExpression
                  .replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(expressionText, replacedExpression));
              }
              JavaCodeStyleManager.getInstance(project).shortenClassReferences(replacedExpression);

            }
            UndoUtil.markPsiFileForUndo(file);
          }
        });
      }
    }

    private static boolean isIterable(PsiMethodCallExpression expression) {
      final PsiElement parent = expression.getParent();
      final PsiMethod method = expression.resolveMethod();
      if (method != null) {
        final PsiClass returnClass = PsiTypesUtil.getPsiClass(method.getReturnType());
        if (returnClass == null || !GuavaFluentIterableConversionRule.FLUENT_ITERABLE.equals(returnClass.getQualifiedName())) {
          return false;
        }
      }
      if (parent instanceof PsiLocalVariable) {
        return isIterable(((PsiLocalVariable)parent).getType());
      }
      else if (parent instanceof PsiReturnStatement) {
        final PsiElement methodOrLambda = PsiTreeUtil.getParentOfType(parent, PsiMethod.class, PsiLambdaExpression.class);
        PsiType methodReturnType = null;
        if (methodOrLambda instanceof PsiMethod) {
          methodReturnType = ((PsiMethod)methodOrLambda).getReturnType();
        }
        else if (methodOrLambda instanceof PsiLambdaExpression) {
          methodReturnType = LambdaUtil.getFunctionalInterfaceReturnType((PsiFunctionalExpression)methodOrLambda);
        }
        return isIterable(methodReturnType);
      }
      return false;
    }

    private static boolean isIterable(@Nullable PsiType type) {
      PsiClass aClass;
      return (aClass = PsiTypesUtil.getPsiClass(type)) != null && CommonClassNames.JAVA_LANG_ITERABLE.equals(aClass.getQualifiedName());
    }

    private static MigrateGuavaTypeFix getFix(ProblemDescriptor descriptor) {
      final QuickFix[] fixes = descriptor.getFixes();
      LOG.assertTrue(fixes != null);
      for (QuickFix fix : fixes) {
        if (fix instanceof MigrateGuavaTypeFix) {
          return (MigrateGuavaTypeFix)fix;
        }
      }
      throw new AssertionError();
    }

    private static boolean performTypeMigration(List<PsiElement> elements, List<PsiType> types) {
      PsiFile containingFile = null;
      SearchScope typeMigrationScope = GlobalSearchScope.EMPTY_SCOPE;
      for (PsiElement element : elements) {
        final PsiFile currentContainingFile = element.getContainingFile();
        if (containingFile == null) {
          containingFile = currentContainingFile;
        }
        else {
          LOG.assertTrue(containingFile.isEquivalentTo(currentContainingFile));
        }
        typeMigrationScope = typeMigrationScope.union(element.getUseScope());
      }
      LOG.assertTrue(containingFile != null);
      if (!FileModificationService.getInstance().prepareFileForWrite(containingFile)) return false;
      try {
        final TypeMigrationRules rules = new TypeMigrationRules();
        rules.setBoundScope(typeMigrationScope);
        TypeMigrationProcessor.runHighlightingTypeMigration(containingFile.getProject(),
                                                            null,
                                                            rules,
                                                            elements.toArray(new PsiElement[elements.size()]),
                                                            createMigrationTypeFunction(elements, types),
                                                            true);
        UndoUtil.markPsiFileForUndo(containingFile);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
      return true;
    }

    private static Function<PsiElement, PsiType> createMigrationTypeFunction(@NotNull final List<PsiElement> elements,
                                                                             @NotNull final List<PsiType> types) {
      LOG.assertTrue(elements.size() == types.size());
      final Map<PsiElement, PsiType> mappings = new HashMap<PsiElement, PsiType>();
      final Iterator<PsiType> typeIterator = types.iterator();
      for (PsiElement element : elements) {
        PsiType type = typeIterator.next();
        mappings.put(element, type);
      }
      return new Function<PsiElement, PsiType>() {
        @Override
        public PsiType fun(PsiElement element) {
          return mappings.get(element);
        }
      };
    }
  }
}