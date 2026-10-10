// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.ModCommandAwareExternalAnnotationsManager;
import com.intellij.codeInsight.daemon.impl.quickfix.DeletePrivateMethodFix;
import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableUtil;
import com.intellij.codeInsight.generation.GetterSetterPrototypeProvider;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.ModUpdateFileText;
import com.intellij.model.SymbolPointer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionListStatement;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiRecordHeader;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSuperExpression;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.PsiTypeParameterListOwner;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.SyntheticElement;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.FindSuperElementsHelper;
import com.intellij.psi.impl.light.LightDefaultConstructor;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ContractConverter;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.modplan.ModPlanChain;
import com.intellij.refactoring.modplan.ModPlanChainBuilder;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.ModPlanRequest;
import com.intellij.refactoring.modplan.ModPlanUiInterpreter;
import com.intellij.refactoring.modplan.impl.ModPlanSubjectConfirmation;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteUsage;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteUsageInfoUtil;
import com.intellij.refactoring.move.moveClassesOrPackages.ModuleInfoUsageDetector;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteAnnotation;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteFieldWriteReference;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteMemberCalleeUsageInfo;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteOverrideAnnotation;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteOverridingMethodUsageInfo;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteParameterCallHierarchyUsageInfo;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceJavaDeleteUsageInfo;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceUsageInfo;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.UsageInfoToUsageConverter;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewManager;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.style.LambdaCanBeReplacedWithAnonymousInspection;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

public class JavaSafeDeleteProcessor extends SafeDeleteProcessorDelegateBase {
  private static final Logger LOG = Logger.getInstance(JavaSafeDeleteProcessor.class);

  @Override
  public boolean handlesElement(PsiElement element) {
    return element instanceof PsiClass ||
           element instanceof PsiMethod ||
           element instanceof PsiRecordComponent ||
           element instanceof PsiField ||
           element instanceof PsiParameter ||
           element instanceof PsiLocalVariable ||
           element instanceof PsiPackage ||
           element instanceof PsiJavaFile;
  }

  @Override
  public @Nullable NonCodeUsageSearchInfo findUsages(@NotNull PsiElement element,
                                                     PsiElement @NotNull [] allElementsToDelete,
                                                     @NotNull List<? super UsageInfo> usages) {
    return findUsagesOf(element, allElementsToDelete, usages);
  }

  static @NotNull NonCodeUsageSearchInfo findUsagesOf(@NotNull PsiElement element,
                                                      PsiElement @NotNull [] allElementsToDelete,
                                                      @NotNull List<? super UsageInfo> usages) {
    Condition<PsiElement> insideDeletedCondition = getUsageInsideDeletedFilter(allElementsToDelete);
    if (element instanceof PsiClass aClass) {
      findClassUsages(aClass, allElementsToDelete, usages);
      if (element instanceof PsiTypeParameter typeParameter) {
        findTypeParameterExternalUsages(typeParameter, usages);
      }
      else {
        appendCallees(aClass, usages);
      }
      ModuleInfoUsageDetector.createSafeDeleteUsageInstance(element.getProject(), allElementsToDelete)
        .detectModuleStatementsUsed(usages, MultiMap.create());
    }
    else if (element instanceof PsiMethod method) {
      insideDeletedCondition = findMethodUsages(method, allElementsToDelete, usages);
    }
    else if (element instanceof PsiField field) {
      insideDeletedCondition = findFieldUsages(field, usages, allElementsToDelete);
    }
    else if (element instanceof PsiRecordComponent component) {
      insideDeletedCondition = findRecordComponentUsages(component, allElementsToDelete, usages);
    }
    else if (element instanceof PsiParameter parameter) {
      LOG.assertTrue(parameter.getDeclarationScope() instanceof PsiMethod);
      findParameterUsages(parameter, allElementsToDelete, usages);
    }
    else if (element instanceof PsiLocalVariable variable) {
      for (PsiReference reference : ReferencesSearch.search(element).asIterable()) {
        PsiReferenceExpression referencedElement = (PsiReferenceExpression)reference.getElement();
        PsiElement statementOrExprInList = PsiTreeUtil.getParentOfType(referencedElement, PsiStatement.class);
        if (statementOrExprInList instanceof PsiExpressionListStatement statement) {
          statementOrExprInList = PsiTreeUtil.findPrevParent(statement.getExpressionList(), referencedElement);
        }

        boolean isSafeToDelete = PsiUtil.isAccessedForWriting(referencedElement);
        boolean hasSideEffects = false;
        if (PsiUtil.isOnAssignmentLeftHand(referencedElement)) {
          PsiExpression rhs = ((PsiAssignmentExpression)referencedElement.getParent()).getRExpression();
          hasSideEffects = RemoveUnusedVariableUtil.checkSideEffects(rhs, variable, new ArrayList<>());
        }
        usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(statementOrExprInList, element, isSafeToDelete && !hasSideEffects));
      }
    }
    if (element instanceof PsiModifierListOwner owner) {
      ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(element.getProject());
      List<PsiFile> annotationsFiles = annotationsManager.findExternalAnnotationsFiles(owner);
      if (annotationsFiles != null) {
        usages.add(new SafeDeleteExternalAnnotationsUsageInfo(owner, owner));
      }
    }
    return new NonCodeUsageSearchInfo(insideDeletedCondition, element);
  }

  /**
   * Runs the chain of {@link #elementsToSearch} with the dialogs of its questions. As in the legacy flow, the default constructor of a
   * class deletes the class, and nothing is asked in a unit test: the questions keep the default answers.
   */
  @Override
  public @Nullable Collection<? extends PsiElement> getElementsToSearch(@NotNull PsiElement element,
                                                                        @Nullable Module module,
                                                                        @NotNull Collection<? extends PsiElement> allElementsToDelete) {
    Project project = element.getProject();
    if (element instanceof PsiMethod method && method.isDefaultConstructor() && !ApplicationManager.getApplication().isUnitTestMode()) {
      return Collections.singletonList(method.getContainingClass());
    }
    ModPlanRequest request = new ModPlanRequest(project, List.of(SymbolPointer.of(element)), ModPlanOptionValues.EMPTY);
    List<SymbolPointer> subjectsToDelete = ContainerUtil.map(allElementsToDelete, SymbolPointer::of);
    ModPlanChain.ChainResult.Finished<List<PsiElement>> finished = ModPlanUiInterpreter.getInstance().interpret(
      SafeDeleteHandler.getRefactoringName(), request, chainBuilder -> {
        PsiElement currentElement = chainBuilder.getRequest().subjects().getFirst().element();
        if (currentElement == null) return chainBuilder.passValue(List.of());
        List<PsiElement> currentElementsToDelete = List.of(ModPlanSafeDeleteUsageInfoUtil.elementsOf(subjectsToDelete));
        return elementsToSearch(chainBuilder, currentElement, module, currentElementsToDelete);
      });
    return finished == null || finished.value().isEmpty() ? null : finished.value();
  }

  /**
   * The elements to search for the deletion of {@code element}, after the questions whether to delete the base methods of a method and a
   * parameter or a type parameter in the whole method hierarchy, see {@link ModPlanJavaSafeDeleteOptions#DELETE_SUPER_METHODS} and
   * {@link ModPlanSafeDeleteOptions#DELETE_HIERARCHY}.
   * <p>
   * The provider and the legacy {@link #getElementsToSearch} share this code: the provider goes on with the elements in
   * {@link ModPlanChain#andThen}, and the legacy flow ends with them.
   *
   * @return the chain that ends with the elements to search; empty if there is nothing to search
   */
  static @NotNull ModPlanChain<List<PsiElement>> elementsToSearch(
    @NotNull ModPlanChainBuilder chainBuilder,
    @NotNull PsiElement element,
    @Nullable Module module,
    @NotNull Collection<? extends PsiElement> allElementsToDelete
  ) {
    if (element instanceof SyntheticElement && !(element instanceof LightDefaultConstructor)) return chainBuilder.passValue(List.of());
    if (element instanceof PsiPackage aPackage && module != null) {
      return chainBuilder.passValue(List.of(aPackage.getDirectories(module.getModuleScope())));
    }
    if (element instanceof PsiJavaFile javaFile) {
      return chainBuilder.passValue(List.of(javaFile.getClasses()));
    }
    if (element instanceof PsiMethod method) {
      if (!JavaSafeDeleteSuperMethodsQuestion.hasSuperMethods(method, allElementsToDelete)) return chainBuilder.passValue(List.of(method));
      SymbolPointer methodSubject = SymbolPointer.of(method);
      List<SymbolPointer> subjectsToDelete = ContainerUtil.map(allElementsToDelete, SymbolPointer::of);
      JavaSafeDeleteSuperMethodsQuestion superMethodsQuestion = new JavaSafeDeleteSuperMethodsQuestion(method);
      return chainBuilder.ask(superMethodsQuestion, answered -> {
        if (!(methodSubject.element() instanceof PsiMethod currentMethod)) return answered.passValue(List.of());
        if (!superMethodsQuestion.shouldDeleteSuperMethods(answered.getRequest().optionValues())) {
          return answered.passValue(List.of(currentMethod));
        }
        List<PsiElement> currentElementsToDelete = List.of(ModPlanSafeDeleteUsageInfoUtil.elementsOf(subjectsToDelete));
        PsiMethod[] candidateMethods = SuperMethodWarningUtil.getTargetMethodCandidates(currentMethod, currentElementsToDelete);
        List<PsiElement> methods = new ArrayList<>(Arrays.asList(candidateMethods));
        if (!methods.contains(currentMethod)) methods.add(currentMethod);
        return answered.passValue(methods);
      });
    }
    if (element instanceof PsiParameter param && param.getDeclarationScope() instanceof PsiMethod method) {
      Set<PsiElement> parametersToDelete = new HashSet<>();
      parametersToDelete.add(element);
      int parameterIndex = method.getParameterList().getParameterIndex(param);
      List<PsiMethod> superMethods = new ArrayList<>(Arrays.asList(method.findDeepestSuperMethods()));
      if (superMethods.isEmpty()) {
        superMethods.add(method);
      }
      ContainerUtil.addAllNotNull(superMethods, FindSuperElementsHelper.getSiblingInheritedViaSubClass(method));
      for (PsiMethod superMethod : superMethods) {
        parametersToDelete.add(superMethod.getParameterList().getParameters()[parameterIndex]);
        OverridingMethodsSearch.search(superMethod).forEach(overrider -> {
          parametersToDelete.add(overrider.getParameterList().getParameters()[parameterIndex].getNavigationElement());
          return true;
        });
      }
      return withHierarchyQuestion(chainBuilder, element, parametersToDelete, JavaRefactoringBundle.message(
        "0.is.a.part.of.method.hierarchy.do.you.want.to.delete.multiple.parameters", UsageViewUtil.getLongName(method)));
    }
    if (element instanceof PsiTypeParameter typeParameter) {
      PsiTypeParameterListOwner owner = typeParameter.getOwner();
      if (owner instanceof PsiMethod method && !owner.hasModifierProperty(PsiModifier.STATIC)) {
        PsiTypeParameterList typeParameterList = owner.getTypeParameterList();
        if (typeParameterList != null) {
          int index = typeParameterList.getTypeParameterIndex(typeParameter);
          if (index >= 0) {
            ArrayList<PsiTypeParameter> overriders = new ArrayList<>();
            overriders.add(typeParameter);
            OverridingMethodsSearch.search(method).forEach(overrider -> {
              PsiTypeParameter[] typeParameters = overrider.getTypeParameters();
              if (index < typeParameters.length) {
                overriders.add(typeParameters[index]);
              }
              return true;
            });
            return withHierarchyQuestion(chainBuilder, element, overriders, JavaRefactoringBundle.message(
              "0.is.a.part.of.method.hierarchy.do.you.want.to.delete.multiple.type.parameters", UsageViewUtil.getLongName(owner)));
          }
        }
      }
    }
    return chainBuilder.passValue(List.of(element));
  }

  /**
   * @param hierarchyElements {@code element} and the same element of the other methods of its hierarchy
   * @param message           the question of the legacy flow, asked if the hierarchy has more than {@code element}
   * @return the chain that ends with the hierarchy, or with {@code element} only if the user does not delete the hierarchy
   */
  private static @NotNull ModPlanChain<List<PsiElement>> withHierarchyQuestion(
    @NotNull ModPlanChainBuilder chainBuilder,
    @NotNull PsiElement element,
    @NotNull Collection<? extends PsiElement> hierarchyElements,
    @NotNull @DialogMessage String message
  ) {
    if (hierarchyElements.size() <= 1) return chainBuilder.passValue(List.copyOf(hierarchyElements));
    List<SymbolPointer> hierarchySubjects = ContainerUtil.map(hierarchyElements, SymbolPointer::of);
    List<SymbolPointer> elementSubjects = List.of(SymbolPointer.of(element));
    ModPlanSubjectConfirmation hierarchyQuestion = new ModPlanSubjectConfirmation(
      ModPlanSafeDeleteOptions.DELETE_HIERARCHY, ModPlanSafeDeleteOptions.HIERARCHY_OF, hierarchyKeyOf(element), project -> {
        int result = Messages.showYesNoCancelDialog(project, message, SafeDeleteHandler.getRefactoringName(), Messages.getQuestionIcon());
        return result == Messages.CANCEL ? null : result == Messages.YES;
      });
    return chainBuilder.ask(hierarchyQuestion, answered -> {
      boolean deletesHierarchy = hierarchyQuestion.isConfirmed(answered.getRequest().optionValues());
      return answered.passValue(List.of(ModPlanSafeDeleteUsageInfoUtil.elementsOf(deletesHierarchy ? hierarchySubjects : elementSubjects)));
    });
  }

  @Override
  public UsageView showUsages(UsageInfo @NotNull [] usages,
                              @NotNull UsageViewPresentation presentation,
                              @NotNull UsageViewManager manager,
                              PsiElement @NotNull [] elements) {
    List<PsiElement> overridingMethods = new ArrayList<>();
    List<UsageInfo> others = new ArrayList<>();
    for (UsageInfo usage : usages) {
      if (usage instanceof SafeDeleteOverridingMethodUsageInfo info) {
        ContainerUtil.addIfNotNull(overridingMethods, info.getOverridingMethod());
      }
      else {
        others.add(usage);
      }
    }

    UsageTarget[] targets = new UsageTarget[elements.length + overridingMethods.size()];
    for (int i = 0; i < targets.length; i++) {
      targets[i] = new PsiElement2UsageTargetAdapter(i < elements.length ? elements[i] : overridingMethods.get(i - elements.length));
    }
    return manager.showUsages(targets, UsageInfoToUsageConverter.convert(elements, others.toArray(UsageInfo.EMPTY_ARRAY)), presentation);
  }

  @Override
  public @NotNull AdditionalElementsData getAdditionalElementsToDelete(@NotNull PsiElement element,
                                                                       @NotNull Collection<? extends PsiElement> allElementsToDelete) {
    return additionalElementsOf(element, allElementsToDelete);
  }

  static @NotNull AdditionalElementsData additionalElementsOf(@NotNull PsiElement element,
                                                              @NotNull Collection<? extends PsiElement> allElementsToDelete) {
    if (element instanceof PsiRecordComponent component) {
      PsiMethod method = JavaPsiRecordUtil.getAccessorForRecordComponent(component);
      List<PsiElement> additional = new ArrayList<>();
      if (method != null && !(method instanceof SyntheticElement) && !allElementsToDelete.contains(method)) {
        additional.add(method);
      }
      PsiClass recordClass = component.getContainingClass();
      assert recordClass != null;
      PsiMethod constructor = JavaPsiRecordUtil.findCanonicalConstructor(recordClass);
      if (constructor == null || constructor instanceof SyntheticElement) return new AdditionalElementsData(additional);
      PsiRecordHeader header = recordClass.getRecordHeader();
      assert header != null;
      int index = ArrayUtil.indexOf(header.getRecordComponents(), component);
      if (index < 0) return new AdditionalElementsData(additional);
      PsiParameter parameter = constructor.getParameterList().getParameter(index);
      if (parameter != null) {
        additional.add(parameter);
      }
      return new AdditionalElementsData(additional);
    }
    if (element instanceof PsiField field) {
      Project project = element.getProject();
      String propertyName = JavaCodeStyleManager.getInstance(project).variableNameToPropertyName(field.getName(), VariableKind.FIELD);

      PsiClass aClass = field.getContainingClass();
      if (aClass != null) {
        boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
        PsiMethod[] getters = GetterSetterPrototypeProvider.findGetters(aClass, propertyName, isStatic);
        if (getters != null) {
          List<PsiMethod> validGetters = new ArrayList<>(1);
          for (PsiMethod getter : getters) {
            if (getter != null && !allElementsToDelete.contains(getter) && getter.isPhysical()) {
              validGetters.add(getter);
            }
          }
          getters = validGetters.isEmpty() ? null : validGetters.toArray(PsiMethod.EMPTY_ARRAY);
        }

        PsiMethod setter = PropertyUtilBase.findPropertySetter(aClass, propertyName, isStatic, false);
        if (setter != null && (allElementsToDelete.contains(setter) || !setter.isPhysical())) {
          setter = null;
        }
        ConfirmationPolicy policy = ConfirmationPolicy.DONT_ASK;
        if (getters != null || setter != null) {
          String title = RefactoringBundle.message("delete.title");
          String message =
            RefactoringMessageUtil.getGetterSetterMessage(field.getName(), title, getters != null ? getters[0] : null, setter);
          policy = new ConfirmationPolicy.Ask(message, true);
        }
        List<PsiElement> elements = new ArrayList<>();
        if (setter != null) elements.add(setter);
        if (getters != null) Collections.addAll(elements, getters);
        return new AdditionalElementsData(elements, policy);
      }
    }
    return AdditionalElementsData.NONE;
  }

  @Override
  public void findConflicts(@NotNull PsiElement element,
                            PsiElement @NotNull [] allElementsToDelete,
                            UsageInfo @NotNull [] usages,
                            @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts) {
    collectConflicts(element, allElementsToDelete, usages, conflicts);
  }

  static void collectConflicts(@NotNull PsiElement element,
                               PsiElement @NotNull [] allElementsToDelete,
                               UsageInfo @NotNull [] usages,
                               @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts) {
    if (element instanceof PsiMethod method) {
      PsiClass containingClass = method.getContainingClass();
      if (containingClass != null && !containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        for (PsiMethod superMethod : method.findSuperMethods()) {
          if (isInside(superMethod, allElementsToDelete)) continue;
          if (superMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
            String message = JavaRefactoringBundle.message("0.implements.1", RefactoringUIUtil.getDescription(element, true),
                                                           RefactoringUIUtil.getDescription(superMethod, true));
            conflicts.putValue(superMethod, StringUtil.capitalize(message));
          }
        }
      }
    }
    else if (element instanceof PsiParameter parameter) {
      PsiElement scope = parameter.getDeclarationScope();
      if (scope instanceof PsiMethod method) {
        for (UsageInfo usage : usages) {
          if (usage.getElement() instanceof PsiMethodReferenceExpression ref && method.equals(ref.resolve())) {
            String message = JavaRefactoringBundle.message("method.reference.will.be.converted.to.lambda.expression.warning");
            conflicts.putValue(ref, StringUtil.capitalize(message));
          }
        }
        collectMethodConflicts(conflicts, method, parameter);
      }
    }
    else if (element instanceof PsiRecordComponent component) {
      PsiClass recordClass = component.getContainingClass();
      assert recordClass != null;
      PsiMethod constructor = JavaPsiRecordUtil.findCanonicalConstructor(recordClass);
      if (constructor != null) {
        PsiRecordHeader header = recordClass.getRecordHeader();
        assert header != null;
        int index = ArrayUtil.indexOf(header.getRecordComponents(), component);
        if (index >= 0) {
          PsiParameter parameter = constructor.getParameterList().getParameter(index);
          if (parameter != null) {
            collectConflicts(parameter, allElementsToDelete, UsageInfo.EMPTY_ARRAY, conflicts);
          }
        }
      }
    }
  }

  /**
   * Runs the chain of {@link #preprocessedUsages} with the dialogs of its questions. In a unit test nothing is asked: the overriding
   * methods, the unused callees and the callers that pass on the parameter are all chosen, as in the legacy flow.
   */
  @Override
  public UsageInfo @Nullable [] preprocessUsages(@NotNull Project project, UsageInfo @NotNull [] usages) {
    ModPlanOptionValues optionValues = ApplicationManager.getApplication().isUnitTestMode()
                            ? ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_UNUSED_CALLEES, true)
                              .with(ModPlanJavaSafeDeleteOptions.DELETE_PARAMETER_IN_CALLERS, true)
                            : ModPlanOptionValues.EMPTY;
    // The request only carries the answers, so its subject is the element of a usage: the deleted elements are not known here.
    PsiElement usageElement = ContainerUtil.find(ContainerUtil.map(usages, UsageInfo::getElement), Objects::nonNull);
    if (usageElement == null) return usages;
    ModPlanRequest request = new ModPlanRequest(project, List.of(SymbolPointer.of(usageElement)), optionValues);
    ModPlanChain.ChainResult.Finished<List<UsageInfo>> finished = ModPlanUiInterpreter.getInstance().interpret(
      SafeDeleteHandler.getRefactoringName(), request,
      chainBuilder -> preprocessedUsages(chainBuilder, usages));
    return finished == null ? null : finished.value().toArray(UsageInfo.EMPTY_ARRAY);
  }

  /**
   * The usages to write, after the questions which overriding methods and unused callees to delete, and in which callers to delete the
   * parameter that only passes on the deleted one, see {@link ModPlanJavaSafeDeleteOptions}.
   * <p>
   * The provider and the legacy {@link #preprocessUsages} share this code: the provider turns the usages into its analysis, and the
   * legacy flow writes them as they are.
   *
   * @return the chain that ends with the usages to write
   */
  static @NotNull ModPlanChain<List<UsageInfo>> preprocessedUsages(
    @NotNull ModPlanChainBuilder chainBuilder,
    UsageInfo @NotNull [] usages
  ) {
    Project project = chainBuilder.getRequest().project();
    List<UsageInfo> result = new ArrayList<>();
    List<SafeDeleteOverridingMethodUsageInfo> overriders = new ArrayList<>();
    List<SafeDeleteParameterCallHierarchyUsageInfo> delegatingParams = new ArrayList<>();
    List<SafeDeleteMemberCalleeUsageInfo> calleesSafeToDelete = new ArrayList<>();
    for (UsageInfo usage : usages) {
      if (usage.isNonCodeUsage) {
        result.add(usage);
      }
      else if (usage instanceof SafeDeleteMemberCalleeUsageInfo info) {
        calleesSafeToDelete.add(info);
      }
      else if (usage instanceof SafeDeleteOverridingMethodUsageInfo info) {
        if (info.getExternalName() != null) overriders.add(info);
      }
      else if (usage instanceof SafeDeleteParameterCallHierarchyUsageInfo info) {
        delegatingParams.add(info);
      }
      else if (usage instanceof SafeDeleteAnnotation info) {
        result.add(new SafeDeleteAnnotation((PsiAnnotation)usage.getElement(), info.findReferencedElement(), true));
      }
      else {
        result.add(usage);
      }
    }

    // The answer about the overriding methods decides whether an @Override stays, so it is kept apart from the other usages.
    List<SafeDeleteOverrideAnnotation> overrideAnnotations = new ArrayList<>();
    if (!overriders.isEmpty()) {
      for (Iterator<UsageInfo> iterator = result.iterator(); iterator.hasNext(); ) {
        if (iterator.next() instanceof SafeDeleteOverrideAnnotation annotation) {
          iterator.remove();
          overrideAnnotations.add(annotation);
        }
      }
    }
    // The questions go in the order of the legacy flow: the overriding methods, the callers, the callees.
    return chainBuilder.passValue(result)
      .andThen(askingOverriders(overriders, overrideAnnotations))
      .andThen(askingCallers(delegatingParams))
      .andThen(askingCallees(project, calleesSafeToDelete));
  }

  /** @return the step that asks which overriding methods to delete, and adds the usages that delete them; none asks nothing */
  private static @NotNull ModPlanChain.AfterValue<List<UsageInfo>, List<UsageInfo>> askingOverriders(
    @NotNull List<SafeDeleteOverridingMethodUsageInfo> overriders,
    @NotNull List<SafeDeleteOverrideAnnotation> overrideAnnotations
  ) {
    if (overriders.isEmpty()) return ModPlanChainBuilder::passValue;
    JavaSafeDeleteSubsetQuestion overridersQuestion = JavaSafeDeleteSubsetQuestion.overriders(overriders);
    return (chainBuilder, foundUsages) -> chainBuilder.ask(overridersQuestion, answered -> {
      Set<String> chosenNames = overridersQuestion.chosenNamesIn(answered.getRequest().optionValues());
      Set<String> overriderNames =
        new HashSet<>(ContainerUtil.mapNotNull(overriders, SafeDeleteOverridingMethodUsageInfo::getExternalName));
      List<UsageInfo> resultUsages = new ArrayList<>(foundUsages);
      for (SafeDeleteOverrideAnnotation annotation : overrideAnnotations) {
        if (annotation.getMethod() instanceof PsiMethod method && overridesOnlyChosen(method, overriderNames, chosenNames)) {
          resultUsages.add(annotation);
        }
      }
      for (SafeDeleteOverridingMethodUsageInfo overrider : overriders) {
        String name = overrider.getExternalName();
        if (name != null && (chosenNames == null || chosenNames.contains(name))) {
          resultUsages.add(overrider);
        }
      }
      return answered.passValue(resultUsages);
    });
  }

  /** @return the step that asks in which callers to delete the parameter, and adds the usages that delete it; none asks nothing */
  private static @NotNull ModPlanChain.AfterValue<List<UsageInfo>, List<UsageInfo>> askingCallers(
    @NotNull List<SafeDeleteParameterCallHierarchyUsageInfo> delegatingParams
  ) {
    if (delegatingParams.isEmpty()) return ModPlanChainBuilder::passValue;
    JavaSafeDeleteSubsetQuestion callersQuestion = JavaSafeDeleteSubsetQuestion.callers(delegatingParams);
    return (chainBuilder, foundUsages) -> chainBuilder.ask(callersQuestion, answered -> {
      Set<String> callerNames = callersQuestion.chosenNamesIn(answered.getRequest().optionValues());
      if (callerNames != null && callerNames.isEmpty()) return answered.passValue(foundUsages);
      List<SafeDeleteParameterCallHierarchyUsageInfo> callerUsages = liveCallerUsages(delegatingParams);
      List<UsageInfo> parameterUsages = Collections.synchronizedList(new ArrayList<>());
      // No names choose the first-level callers, as the chooser shows them.
      SafeDeleteJavaCallerChooser.addChosenCallerUsages(callerUsages, callerNames == null ? Set.of() : callerNames, new HashSet<>(),
                                                        parameterUsages);
      return answered.passValue(ContainerUtil.concat(foundUsages, parameterUsages));
    });
  }

  /** @return the step that asks which unused callees to delete, and adds the usages that delete them; none asks nothing */
  private static @NotNull ModPlanChain.AfterValue<List<UsageInfo>, List<UsageInfo>> askingCallees(
    @NotNull Project project,
    @NotNull List<SafeDeleteMemberCalleeUsageInfo> calleesSafeToDelete
  ) {
    PsiMember member = calleesSafeToDelete.isEmpty() ? null : calleesSafeToDelete.getFirst().getCallerMember();
    if (member == null) return ModPlanChainBuilder::passValue;
    JavaSafeDeleteSubsetQuestion calleesQuestion = JavaSafeDeleteSubsetQuestion.callees(member, calleesSafeToDelete);
    return (chainBuilder, foundUsages) -> chainBuilder.ask(calleesQuestion, answered -> {
      ModPlanOptionValues answers = answered.getRequest().optionValues();
      PsiMember currentMember = calleesSafeToDelete.getFirst().getCallerMember();
      Set<String> calleeNames = calleesQuestion.chosenNamesIn(answers);
      if (calleeNames != null && calleeNames.isEmpty() || currentMember == null) {
        return answered.passValue(foundUsages);
      }
      List<SafeDeleteMemberCalleeUsageInfo> topLevelCallees = liveCalleeUsages(calleesSafeToDelete);
      List<UsageInfo> calleeUsages = Collections.synchronizedList(new ArrayList<>());
      List<UsageInfo> list = chosenCallees(currentMember, topLevelCallees, calleeNames);
      calleeUsages.addAll(list);
      List<PsiElement> methodsToDelete = new ArrayList<>();
      for (UsageInfo info : list) {
        methodsToDelete.add(info.getElement());
      }
      methodsToDelete.add(currentMember);
      Condition<PsiElement> insideDeletedCondition = getUsageInsideDeletedFilter(methodsToDelete.toArray(PsiElement.EMPTY_ARRAY));
      for (UsageInfo info : list) {
        PsiElement psi = info.getElement();
        JavaRefactoringSettings refactoringSettings = JavaRefactoringSettings.getInstance();
        // The answers of the run, else the settings of the rename, as the legacy flow reads them.
        boolean searchNonJava = answers.has(ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE)
                                ? answers.get(ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE)
                                : psi instanceof PsiMethod
                                  ? refactoringSettings.RENAME_SEARCH_FOR_TEXT_FOR_METHOD
                                  : refactoringSettings.RENAME_SEARCH_FOR_TEXT_FOR_FIELD;
        boolean searchInCommentsAndStrings = answers.has(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS)
                                             ? answers.get(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS)
                                             : psi instanceof PsiMethod
                                               ? refactoringSettings.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD
                                               : refactoringSettings.RENAME_SEARCH_IN_COMMENTS_FOR_FIELD;
        SafeDeleteProcessor.addNonCodeUsages(psi, GlobalSearchScope.projectScope(project), calleeUsages, insideDeletedCondition,
                                             searchNonJava, searchInCommentsAndStrings);
      }
      return answered.passValue(ContainerUtil.concat(foundUsages, calleeUsages));
    });
  }

  /**
   * @param overriderNames the external names of the offered overriding methods
   * @param chosenNames    the external names of the chosen ones; null chooses all
   * @return whether every offered overriding method that {@code method} overrides is chosen: its {@code @Override} stays otherwise
   */
  private static boolean overridesOnlyChosen(@NotNull PsiMethod method,
                                             @NotNull Set<String> overriderNames,
                                             @Nullable Set<String> chosenNames) {
    if (chosenNames == null) return true;
    for (PsiMethod superMethod : method.findSuperMethods()) {
      String name = externalNameOf(superMethod);
      if (name != null && overriderNames.contains(name) && !chosenNames.contains(name)) return false;
    }
    return true;
  }

  /** @return the usages whose elements all still exist: a usage keeps smart pointers, so it survives the question about the callers */
  static @NotNull List<SafeDeleteParameterCallHierarchyUsageInfo> liveCallerUsages(
    @NotNull List<SafeDeleteParameterCallHierarchyUsageInfo> usages
  ) {
    return ContainerUtil.filter(usages, usage -> usage.getCalledMethod() != null && usage.findReferencedElement() != null &&
                                                 usage.getCallerMethod() != null && usage.getParameterInCaller() != null);
  }

  /** @return the usages whose callee and caller still exist: a usage keeps smart pointers, so it survives the question about the callees */
  static @NotNull List<SafeDeleteMemberCalleeUsageInfo> liveCalleeUsages(@NotNull List<SafeDeleteMemberCalleeUsageInfo> usages) {
    return ContainerUtil.filter(usages, usage -> usage.getCalledElement() != null && usage.getCallerMember() != null);
  }

  /**
   * @param chosenNames the external names of the chosen callees, the nested ones included; null chooses all the top-level callees
   * @return the callees that {@link SafeDeleteJavaCalleeChooser} returns when the elements with these names are checked: it descends
   * only into the chosen ones, and takes the deepest super methods of a chosen method instead of the method
   */
  private static @NotNull List<UsageInfo> chosenCallees(@NotNull PsiMember member,
                                                        @NotNull List<SafeDeleteMemberCalleeUsageInfo> topLevelCallees,
                                                        @Nullable Set<String> chosenNames) {
    if (chosenNames == null) return new ArrayList<>(topLevelCallees);
    Map<PsiElement, UsageInfo> resultCallees = new LinkedHashMap<>();
    collectChosenCallees(member, ContainerUtil.mapNotNull(topLevelCallees, SafeDeleteMemberCalleeUsageInfo::getCalledElement), chosenNames,
                         new HashSet<>(), resultCallees);
    return new ArrayList<>(resultCallees.values());
  }

  private static void collectChosenCallees(@NotNull PsiMember member,
                                           @NotNull List<PsiElement> callees,
                                           @NotNull Set<String> names,
                                           @NotNull Set<PsiElement> visitedCallees,
                                           @NotNull Map<PsiElement, UsageInfo> resultCallees) {
    for (PsiElement callee : callees) {
      if (!visitedCallees.add(callee)) continue;
      PsiElement[] superMethods = callee instanceof PsiMethod method ? method.findDeepestSuperMethods() : PsiElement.EMPTY_ARRAY;
      List<PsiElement> deletedElements = superMethods.length == 0 ? List.of(callee) : List.of(superMethods);
      if (!ContainerUtil.exists(deletedElements, element -> names.contains(externalNameOf(element)))) continue;
      for (PsiElement element : deletedElements) {
        resultCallees.putIfAbsent(element, new SafeDeleteMemberCalleeUsageInfo(element, member));
      }
      if (callee instanceof PsiMember calleeMember) {
        List<PsiElement> nestedCallees = DeletePrivateMethodFix.computeReferencedCodeSafeToDelete(calleeMember);
        nestedCallees.remove(member);
        collectChosenCallees(member, nestedCallees, names, visitedCallees, resultCallees);
      }
    }
  }

  /**
   * @return the key of a parameter or a type parameter in {@link ModPlanSafeDeleteOptions#HIERARCHY_OF}: the external name of a parameter,
   * and the external name of its method and its name for a type parameter; null if it has none
   */
  private static @Nullable String hierarchyKeyOf(@NotNull PsiElement element) {
    if (!(element instanceof PsiTypeParameter typeParameter)) return externalNameOf(element);
    PsiTypeParameterListOwner owner = typeParameter.getOwner();
    String ownerName = owner == null ? null : externalNameOf(owner);
    return ownerName == null ? null : ownerName + " " + typeParameter.getName();
  }

  /** @return the name under which a chosen method or field is listed in a text option; null if it has none */
  static @Nullable String externalNameOf(@NotNull PsiElement element) {
    return element instanceof PsiModifierListOwner owner ? PsiFormatUtil.getExternalName(owner) : null;
  }

  @Override
  public void prepareForDeletion(@NotNull PsiElement element) {
    prepareElementForDeletion(element);
  }

  static void prepareElementForDeletion(@NotNull PsiElement element) {
    if (element instanceof PsiParameter parameter
        && element.getParent() instanceof PsiParameterList parameterList
        && parameterList.getParent() instanceof PsiMethod method) {
      PsiAnnotation contract = JavaMethodContractUtil.findContractAnnotation(method);
      if (contract != null) {
        ParameterInfoImpl[] info = ParameterInfoImpl.fromMethodExceptParameter(method, parameter);
        try {
          String[] names = ContainerUtil.map(parameterList.getParameters(), PsiParameter::getName, ArrayUtilRt.EMPTY_STRING_ARRAY);
          PsiAnnotation newContract = ContractConverter.convertContract(method, names, info);
          if (newContract != null && newContract != contract) {
            contract.replace(newContract);
          }
        }
        catch (ContractConverter.ContractConversionException ignored) {
        }
      }
    }
  }

  @Override
  public boolean isToSearchInComments(PsiElement element) {
    return searchesInComments(element);
  }

  static boolean searchesInComments(PsiElement element) {
    if (element instanceof PsiClass) {
      return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_CLASS;
    }
    if (element instanceof PsiMethod) {
      return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_METHOD;
    }
    if (element instanceof PsiVariable) {
      return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE;
    }
    if (element instanceof PsiPackage) {
      return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE;
    }
    return false;
  }

  @Override
  public void setToSearchInComments(PsiElement element, boolean enabled) {
    setSearchesInComments(element, enabled);
  }

  static void setSearchesInComments(PsiElement element, boolean enabled) {
    if (element instanceof PsiClass) {
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_CLASS = enabled;
    }
    else if (element instanceof PsiMethod) {
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_METHOD = enabled;
    }
    else if (element instanceof PsiVariable) {
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE = enabled;
    }
    else if (element instanceof PsiPackage) {
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE = enabled;
    }
  }

  @Override
  public boolean isToSearchForTextOccurrences(PsiElement element) {
    return searchesForText(element);
  }

  static boolean searchesForText(PsiElement element) {
    if (element instanceof PsiClass) {
      return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_CLASS;
    }
    if (element instanceof PsiMethod) {
      return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_METHOD;
    }
    if (element instanceof PsiVariable) {
      return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE;
    }
    if (element instanceof PsiPackage) {
      return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE;
    }
    return false;
  }

  @Override
  public void setToSearchForTextOccurrences(PsiElement element, boolean enabled) {
    setSearchesForText(element, enabled);
  }

  static void setSearchesForText(PsiElement element, boolean enabled) {
    if (element instanceof PsiClass) {
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_CLASS = enabled;
    }
    else if (element instanceof PsiMethod) {
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_METHOD = enabled;
    }
    else if (element instanceof PsiVariable) {
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE = enabled;
    }
    else if (element instanceof PsiPackage) {
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE = enabled;
    }
  }

  private static Condition<PsiElement> getUsageInsideDeletedFilter(PsiElement[] allElementsToDelete) {
    return usage -> !(usage instanceof PsiFile) && isInside(usage, allElementsToDelete);
  }

  private static void findClassUsages(PsiClass psiClass, PsiElement[] allElementsToDelete, List<? super UsageInfo> usages) {
    String qualifiedName = psiClass.getQualifiedName();
    boolean annotationType = psiClass.isAnnotationType() && qualifiedName != null;

    PsiElement[] topElementsToDelete = Arrays.stream(allElementsToDelete).map(element -> {
      if (element instanceof PsiClass) {
        PsiElement parent = element.getParent();
        if (parent instanceof PsiClassOwner owner) {
          PsiClass[] classes = owner.getClasses();
          if (classes.length == 1 && classes[0] == element) {
            return element.getContainingFile();
          }
        }
      }
      return element;
    }).toArray(PsiElement[]::new);
    ReferencesSearch.search(psiClass).forEach(reference -> {
      PsiElement element = reference.getElement();

      if (!isInside(element, topElementsToDelete)) {
        PsiElement parent = element.getParent();
        JavaSafeDeleteDelegate safeDeleteDelegate = JavaSafeDeleteDelegate.EP.forLanguage(element.getLanguage());
        UsageInfo usageInfo = safeDeleteDelegate != null ? safeDeleteDelegate.createExtendsListUsageInfo(psiClass, reference) : null;
        if (usageInfo != null) {
          usages.add(usageInfo);
          return true;
        }
        LOG.assertTrue(element.getTextRange() != null);
        PsiFile containingFile = psiClass.getContainingFile();
        boolean sameFileWithSingleClass = false;
        if (containingFile instanceof PsiClassOwner owner) {
          PsiClass[] classes = owner.getClasses();
          sameFileWithSingleClass = classes.length == 1 &&
                                    classes[0] == psiClass &&
                                    element.getContainingFile() == containingFile;
        }

        boolean safeDelete = sameFileWithSingleClass || isInNonStaticImport(element);
        if (annotationType && parent instanceof PsiAnnotation annotation) {
          usages.add(new SafeDeleteAnnotation(annotation, psiClass, safeDelete));
        }
        else {
          usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, psiClass, safeDelete));
        }
      }
      return true;
    });
  }

  private static boolean isInNonStaticImport(PsiElement element) {
    return ImportSearcher.getImport(element, true) != null;
  }

  private static void findTypeParameterExternalUsages(PsiTypeParameter typeParameter, List<? super UsageInfo> usages) {
    PsiTypeParameterListOwner owner = typeParameter.getOwner();
    if (owner != null) {
      PsiTypeParameterList parameterList = owner.getTypeParameterList();
      if (parameterList != null) {
        int paramsCount = parameterList.getTypeParameters().length;
        int index = parameterList.getTypeParameterIndex(typeParameter);

        ReferencesSearch.search(owner).forEach(reference -> {
          JavaSafeDeleteDelegate safeDeleteDelegate = JavaSafeDeleteDelegate.EP.forLanguage(reference.getElement().getLanguage());
          if (safeDeleteDelegate != null) {
            safeDeleteDelegate.createJavaTypeParameterUsageInfo(reference, usages, typeParameter, paramsCount, index);
          }
          return true;
        });
      }
    }
  }

  private static @Nullable Condition<PsiElement> findMethodUsages(PsiMethod psiMethod,
                                                                  PsiElement[] allElementsToDelete,
                                                                  @NotNull List<? super UsageInfo> usages) {
    Collection<PsiReference> references = ReferencesSearch.search(psiMethod).findAll();

    if (psiMethod.isConstructor()) {
      return findConstructorUsages(psiMethod, references, usages, allElementsToDelete);
    }
    PsiMethod[] methods =
      OverridingMethodsSearch.search(psiMethod).filtering(m -> !(m instanceof LightRecordMethod)).toArray(PsiMethod.EMPTY_ARRAY);
    PsiMethod[] overridingMethods = removeDeletedMethods(methods, allElementsToDelete);
    findFunctionalExpressions(usages, ArrayUtil.prepend(psiMethod, overridingMethods));

    HashMap<PsiMethod, Collection<PsiReference>> methodToReferences = new HashMap<>();
    for (PsiMethod overridingMethod : overridingMethods) {
      Collection<PsiReference> overridingReferences = ReferencesSearch.search(overridingMethod).findAll();
      methodToReferences.put(overridingMethod, overridingReferences);
    }
    Set<PsiMethod> validOverriding =
      validateOverridingMethods(psiMethod, references, Arrays.asList(overridingMethods), methodToReferences, usages, allElementsToDelete);
    for (PsiReference reference : references) {
      PsiElement element = reference.getElement();
      if (!isInside(element, allElementsToDelete) && !isInside(element, validOverriding)) {
        boolean inStaticImport = PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class) == null;
        usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, psiMethod, !inStaticImport));
      }
    }

    appendCallees(psiMethod, usages);

    return usage -> {
      if (usage instanceof PsiFile) return false;
      return isInside(usage, allElementsToDelete) || isInside(usage,  validOverriding);
    };
  }

  private static void appendCallees(@NotNull PsiMember method, @NotNull List<? super UsageInfo> usages) {
    for (PsiElement callee : DeletePrivateMethodFix.computeReferencedCodeSafeToDelete(method)) {
      usages.add(new SafeDeleteMemberCalleeUsageInfo(callee, method));
    }
  }

  private static void findFunctionalExpressions(List<? super UsageInfo> usages, PsiMethod... methods) {
    for (PsiMethod method : methods) {
      PsiClass containingClass = method.getContainingClass();
      FunctionalExpressionSearch.search(method).forEach(expression -> {
        usages.add(new SafeDeleteFunctionalExpressionUsageInfo(expression, containingClass, false));
        return true;
      });
    }
  }

  private static PsiMethod[] removeDeletedMethods(PsiMethod[] methods, PsiElement[] allElementsToDelete) {
    ArrayList<PsiMethod> list = new ArrayList<>();
    for (PsiMethod method : methods) {
      boolean isDeleted = false;
      for (PsiElement element : allElementsToDelete) {
        if (element == method) {
          isDeleted = true;
          break;
        }
      }
      if (!isDeleted) {
        list.add(method);
      }
    }
    return list.toArray(PsiMethod.EMPTY_ARRAY);
  }

  private static @Nullable Condition<PsiElement> findConstructorUsages(PsiMethod constructor,
                                                                       Collection<PsiReference> originalReferences,
                                                                       @NotNull List<? super UsageInfo> usages,
                                                                       PsiElement[] allElementsToDelete) {
    if (isTheOnlyEmptyDefaultConstructor(constructor)) return null;

    Set<PsiMethod> newConstructors = new HashSet<>();
    newConstructors.add(constructor);
    Map<PsiMethod, Collection<PsiReference>> constructorsToRefs = new HashMap<>();
    constructorsToRefs.put(constructor, originalReferences);
    HashSet<PsiMethod> passConstructors = new HashSet<>();
    do {
      passConstructors.clear();
      for (PsiMethod method : newConstructors) {
        Collection<PsiReference> references = constructorsToRefs.get(method);
        for (PsiReference reference : references) {
          PsiMethod overridingConstructor = getOverridingConstructorOfSuperCall(reference.getElement());
          if (overridingConstructor != null && !constructorsToRefs.containsKey(overridingConstructor)) {
            Collection<PsiReference> overridingConstructorReferences = ReferencesSearch.search(overridingConstructor).findAll();
            constructorsToRefs.put(overridingConstructor, overridingConstructorReferences);
            passConstructors.add(overridingConstructor);
          }
        }
      }
      newConstructors.clear();
      newConstructors.addAll(passConstructors);
    }
    while(!newConstructors.isEmpty());

    Set<PsiMethod> validOverriding =
            validateOverridingMethods(constructor, originalReferences, constructorsToRefs.keySet(), constructorsToRefs, usages,
                                      allElementsToDelete);

    return usage -> {
      if (usage instanceof PsiFile) return false;
      return isInside(usage, allElementsToDelete) || isInside(usage, validOverriding);
    };
  }

  private static boolean isTheOnlyEmptyDefaultConstructor(PsiMethod constructor) {
    if (!constructor.getParameterList().isEmpty()) return false;
    PsiCodeBlock body = constructor.getBody();
    if (body != null && !body.isEmpty()) return false;
    PsiClass aClass = constructor.getContainingClass();
    if (aClass == null) return false;
    return aClass.getConstructors().length == 1;
  }

  private static Set<PsiMethod> validateOverridingMethods(PsiMethod originalMethod,
                                                          @NotNull Collection<? extends PsiReference> originalReferences,
                                                          @NotNull Collection<? extends PsiMethod> overridingMethods,
                                                          Map<PsiMethod, Collection<PsiReference>> methodToReferences,
                                                          @NotNull List<? super UsageInfo> usages,
                                                          PsiElement @NotNull [] allElementsToDelete) {
    Set<PsiMethod> validOverriding = new LinkedHashSet<>(overridingMethods);
    Set<PsiMethod> multipleInterfaceImplementations = new HashSet<>();
    boolean anyNewBadRefs;
    do {
      anyNewBadRefs = false;
      for (PsiMethod overridingMethod : overridingMethods) {
        if (validOverriding.contains(overridingMethod)) {
          Collection<PsiReference> overridingReferences = methodToReferences.get(overridingMethod);
          boolean anyOverridingRefs = false;
          for (PsiReference overridingReference : overridingReferences) {
            PsiElement element = overridingReference.getElement();
            if (!isInside(element, allElementsToDelete) && !isInside(element, validOverriding)) {
              anyOverridingRefs = true;
              break;
            }
          }
          if (!anyOverridingRefs && isMultipleInterfacesImplementation(overridingMethod, originalMethod, allElementsToDelete)) {
            anyOverridingRefs = true;
            multipleInterfaceImplementations.add(overridingMethod);
          }

          if (anyOverridingRefs) {
            validOverriding.remove(overridingMethod);
            anyNewBadRefs = true;

            for (PsiReference reference : originalReferences) {
              PsiElement element = reference.getElement();
              if (!isInside(element, allElementsToDelete) && !isInside(element, overridingMethods)) {
                usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, originalMethod, false));
                validOverriding.clear();
              }
            }
          }
        }
      }
    }
    while(anyNewBadRefs && !validOverriding.isEmpty());

    for (PsiMethod method : validOverriding) {
      if (method != originalMethod) {
        usages.add(new SafeDeleteOverridingMethodUsageInfo(method, originalMethod));
      }
    }

    for (PsiMethod method : overridingMethods) {
      if (!multipleInterfaceImplementations.contains(method)) {
        JavaSafeDeleteDelegate safeDeleteDelegate = JavaSafeDeleteDelegate.EP.forLanguage(method.getLanguage());
        if (safeDeleteDelegate != null) {
          safeDeleteDelegate.createCleanupOverriding(method, allElementsToDelete, usages);
        }
      }
    }
    return validOverriding;
  }

  private static boolean isMultipleInterfacesImplementation(PsiMethod method,
                                                            PsiMethod originalMethod,
                                                            PsiElement[] allElementsToDelete) {
    for (PsiMethod superMethod: method.findSuperMethods()) {
      if (ArrayUtil.find(allElementsToDelete, superMethod) < 0 && !MethodSignatureUtil.isSuperMethod(originalMethod, superMethod)) {
        return true;
      }
    }
    return false;
  }

  private static @Nullable PsiMethod getOverridingConstructorOfSuperCall(PsiElement element) {
    if (element instanceof PsiReferenceExpression ref &&
        JavaKeywords.SUPER.equals(element.getText()) &&
        ref.getParent() instanceof PsiMethodCallExpression call &&
        call.getParent() instanceof PsiExpressionStatement st &&
        st.getParent() instanceof PsiCodeBlock block &&
        block.getParent() instanceof PsiMethod constructor &&
        constructor.isConstructor()) {
      return constructor;
    }
    return null;
  }

  static boolean canBePrivate(@NotNull PsiMethod method, PsiElement @NotNull [] allElementsToDelete) {
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return false;

    JavaPsiFacade facade = JavaPsiFacade.getInstance(method.getProject());
    PsiResolveHelper resolveHelper = facade.getResolveHelper();
    PsiElementFactory factory = facade.getElementFactory();
    PsiModifierList privateModifierList = factory.createMethod("x3", PsiTypes.voidType()).getModifierList();
    privateModifierList.setModifierProperty(PsiModifier.PRIVATE, true);
    for (PsiReference reference : ReferencesSearch.search(method).findAll()) {
      PsiElement element = reference.getElement();
      if (!isInside(element, allElementsToDelete) && !resolveHelper.isAccessible(method, privateModifierList, element, null, null)) {
        return false;
      }
    }
    return true;
  }

  private static Condition<PsiElement> findFieldUsages(PsiField psiField, List<? super UsageInfo> usages, PsiElement[] allElementsToDelete) {
    Condition <PsiElement> isInsideDeleted = getUsageInsideDeletedFilter(allElementsToDelete);
    Set<PsiParameter> parameters = new LinkedHashSet<>();
    ReferencesSearch.search(psiField).forEach(reference -> {
      PsiElement element = reference.getElement();
      if (!isInsideDeleted.test(element)) {
        if (element.getParent() instanceof PsiAssignmentExpression assignment && element == assignment.getLExpression()) {
          usages.add(new SafeDeleteFieldWriteReference(assignment, psiField));
          PsiExpression rExpression = PsiUtil.skipParenthesizedExprDown(assignment.getRExpression());
          if (rExpression instanceof PsiReferenceExpression ref && ref.resolve() instanceof PsiParameter parameter &&
              parameter.getDeclarationScope() instanceof PsiMethod method && method.isConstructor()) {
            parameters.add(parameter);
          }
        }
        else {
          TextRange range = reference.getRangeInElement();
          boolean inStaticImport = PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class) == null;
          usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(reference.getElement(), psiField, range.getStartOffset(),
                                                                range.getEndOffset(), false, !inStaticImport));
        }
      }
      return true;
    });

    PsiClass containingClass = psiField.getContainingClass();
    if (containingClass != null) {
      PsiMethod setterPrototype = PropertyUtilBase.generateSetterPrototype(psiField, containingClass);
      PsiParameter setterParameter = setterPrototype.getParameterList().getParameters()[0];
      for (PsiParameter parameter : parameters) {
        PsiElement scope = parameter.getDeclarationScope();
        if (scope instanceof PsiMethod method) {
          if (!ReferencesSearch.search(parameter, new LocalSearchScope(scope)).forEach(ref -> {
            PsiElement element = ref.getElement();
            if (element instanceof PsiReferenceExpression &&
                PsiUtil.skipParenthesizedExprUp(element.getParent()) instanceof PsiAssignmentExpression assignment &&
                PsiUtil.skipParenthesizedExprDown(assignment.getLExpression()) instanceof PsiReferenceExpression r &&
                r.resolve() == psiField) {
              return true;
            }
            return false;
          })) continue;
          usages.add(createParameterCallHierarchyUsageInfo(setterPrototype, setterParameter, method, parameter));
        }
      }
    }
    else {
      LOG.assertTrue(!psiField.isPhysical(), "No containing class for field: " + psiField.getClass());
    }
    appendCallees(psiField, usages);
    return isInsideDeleted;
  }

  private static SafeDeleteParameterCallHierarchyUsageInfo createParameterCallHierarchyUsageInfo(PsiMethod called,
                                                                                                 PsiParameter calledParameter,
                                                                                                 PsiMethod caller,
                                                                                                 PsiParameter parameterInCaller) {
    return ApplicationManager.getApplication().isUnitTestMode()
           ? new SafeDeleteParameterCallHierarchyUsageInfo(caller, parameterInCaller, caller, parameterInCaller)
           : new SafeDeleteParameterCallHierarchyUsageInfo(called, calledParameter, caller, parameterInCaller);
  }

  private static Condition<PsiElement> findRecordComponentUsages(PsiRecordComponent component,
                                                                 PsiElement @NotNull [] allElementsToDelete,
                                                                 List<? super UsageInfo> usages) {
    Condition <PsiElement> isInsideDeleted = getUsageInsideDeletedFilter(allElementsToDelete);
    PsiClass recordClass = component.getContainingClass();
    assert recordClass != null;
    PsiMethod constructor = JavaPsiRecordUtil.findCanonicalConstructor(recordClass);
    if (constructor != null) {
      PsiRecordHeader header = recordClass.getRecordHeader();
      assert header != null;
      int index = ArrayUtil.indexOf(header.getRecordComponents(), component);
      if (index < 0) return isInsideDeleted;
      ReferencesSearch.search(constructor).asIterable().forEach(ref -> {
        PsiElement element = ref.getElement();
        if (!isInsideDeleted.test(element)) {
          JavaSafeDeleteDelegate safeDeleteDelegate = JavaSafeDeleteDelegate.EP.forLanguage(element.getLanguage());
          if (safeDeleteDelegate != null) {
            safeDeleteDelegate.createUsageInfoForParameter(ref, usages, component, index, component.isVarArgs());
          }
        }
      });
    }
    ReferencesSearch.search(component).asIterable().forEach(ref -> {
      PsiElement element = ref.getElement();
      if (!isInsideDeleted.test(element)) {
        boolean javadoc = element instanceof PsiDocParamRef;
        PsiElement parent = element.getParent();
        usages.add(parent instanceof PsiAssignmentExpression assignment && element == assignment.getLExpression()
                   ? new SafeDeleteFieldWriteReference(assignment, component)
                   : new SafeDeleteReferenceJavaDeleteUsageInfo(javadoc ? parent : element, component, javadoc));
      }
    });
    return isInsideDeleted;
  }

  private static void findParameterUsages(@NotNull PsiParameter parameter,
                                          PsiElement @NotNull [] allElementsToDelete,
                                          @NotNull List<? super UsageInfo> usages) {
    PsiMethod method = (PsiMethod)parameter.getDeclarationScope();
    int parameterIndex = method.getParameterList().getParameterIndex(parameter);
    if (parameterIndex < 0) return;
    //search for refs to current method only, do not search for refs to overriding methods, they'll be searched separately
    ReferencesSearch.search(method).forEach(reference -> {
      PsiElement element = reference.getElement();
      JavaSafeDeleteDelegate safeDeleteDelegate = JavaSafeDeleteDelegate.EP.forLanguage(element.getLanguage());
      if (safeDeleteDelegate != null) {
        safeDeleteDelegate.createUsageInfoForParameter(reference, usages, parameter, parameterIndex, parameter.isVarArgs());
      }
      if (!parameter.isVarArgs() && !JavaPsiConstructorUtil.isSuperConstructorCall(element.getParent())) {
        PsiParameter paramInCaller = SafeDeleteJavaCallerChooser.isTheOnlyOneParameterUsage(element.getParent(), parameterIndex, method);
        if (paramInCaller != null) {
          PsiMethod callerMethod = (PsiMethod)paramInCaller.getDeclarationScope();
          usages.add(createParameterCallHierarchyUsageInfo( method, parameter, callerMethod, paramInCaller));
        }
      }
      return true;
    });

    ReferencesSearch.search(parameter).forEach(reference -> {
      PsiElement element = reference.getElement();
      PsiDocTag docTag = PsiTreeUtil.getParentOfType(element, PsiDocTag.class);
      if (docTag != null) {
        usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(docTag, parameter, true));
        return true;
      }
      if (PsiUtil.skipParenthesizedExprUp(element.getParent()) instanceof PsiAssignmentExpression assignment &&
          PsiTreeUtil.isAncestor(assignment.getRExpression(), element, false) &&
          assignment.getLExpression() instanceof PsiReferenceExpression ref &&
          ref.resolve() instanceof PsiField field) {
        PsiRecordComponent component = JavaPsiRecordUtil.getComponentForField(field);
        if (component != null && isInside(component, allElementsToDelete)) return true;
      }

      boolean isSafeDelete = false;
      if (element.getParent().getParent() instanceof PsiMethodCallExpression call) {
        if (JavaPsiConstructorUtil.isSuperConstructorCall(call)) {
          isSafeDelete = true;
        }
        else if (call.getMethodExpression().getQualifierExpression() instanceof PsiSuperExpression) {
          PsiMethod superMethod = call.resolveMethod();
          if (superMethod != null && MethodSignatureUtil.isSuperMethod(superMethod, method)) {
            isSafeDelete = true;
          }
        }
      }
      usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, parameter, isSafeDelete));
      return true;
    });

    findFunctionalExpressions(usages, method);
  }

  private static boolean isInside(@NotNull PsiElement place, PsiElement @NotNull [] ancestors) {
    return isInside(place, Arrays.asList(ancestors));
  }

  private static boolean isInside(@NotNull PsiElement place, @NotNull Collection<? extends PsiElement> ancestors) {
    return ContainerUtil.exists(ancestors, element -> isInside(place, element));
  }

  public static boolean isInside(@NotNull PsiElement place, PsiElement ancestor) {
    if (SafeDeleteProcessor.isInside(place, ancestor)) return true;
    // file will be deleted on class deletion
    return PsiTreeUtil.getParentOfType(place, PsiComment.class, false) != null &&
           ancestor instanceof PsiClass aClass &&
           aClass.getParent() instanceof PsiJavaFile file &&
           PsiTreeUtil.isAncestor(file, place, false) &&
           file.getClasses().length == 1;
  }

  public static void collectMethodConflicts(MultiMap<PsiElement, @DialogMessage String> conflicts,
                                            @NotNull PsiMethod method,
                                            PsiParameter parameter) {
    PsiClass containingClass = method.getContainingClass();
    if (containingClass != null) {
      PsiMethod methodCopy = (PsiMethod)method.copy();
      if (!containingClass.isRecord() || !JavaPsiRecordUtil.isCompactConstructor(method)) {
        int parameterIndex = method.getParameterList().getParameterIndex(parameter);
        methodCopy.getParameterList().getParameters()[parameterIndex].delete();
      }
      ConflictsUtil.checkMethodConflicts(containingClass, method, methodCopy, conflicts);
    }
  }

  private static class SafeDeleteFunctionalExpressionUsageInfo extends SafeDeleteReferenceUsageInfo implements ModPlanSafeDeleteUsage {
    private final boolean myIsMethodUsage;

    SafeDeleteFunctionalExpressionUsageInfo(@NotNull PsiElement element, PsiElement referencedElement, boolean isMethodUsage) {
      super(element, referencedElement, isSafeToDelete(element, isMethodUsage));
      myIsMethodUsage = isMethodUsage;
    }

    private static boolean isSafeToDelete(@NotNull PsiElement element, boolean isMethodUsage) {
      if (isMethodUsage) {
        if (element instanceof PsiLambdaExpression) {
          return LambdaCanBeReplacedWithAnonymousInspection.isConvertibleLambdaExpression(element);
        }
        if (element instanceof PsiMethodReferenceExpression) {
          // todo check if we can convert method ref to anonymous class
        }
      }
      return false;
    }

    @Override
    public void deleteElement() {
      toAnonymousClass(getElement(), myIsMethodUsage);
    }

    private static void toAnonymousClass(@Nullable PsiElement element, boolean isMethodUsage) {
      if (!isMethodUsage) return;
      if (element instanceof PsiLambdaExpression lambda) {
        PsiAnonymousClass aClass = LambdaCanBeReplacedWithAnonymousInspection.doFix(lambda.getProject(), lambda);
        if (aClass == null) return;
        PsiFile file = aClass.getContainingFile();
        if (file == null || !JavaCodeStyleSettings.getInstance(file).INSERT_OVERRIDE_ANNOTATION) return;
        PsiMethod[] methods = aClass.getMethods();
        if (methods.length != 1) return;
        PsiAnnotation overrideAnnotation = AnnotationUtil.findAnnotation(methods[0], true, CommonClassNames.JAVA_LANG_OVERRIDE);
        if (overrideAnnotation != null) {
          overrideAnnotation.delete();
        }
      }
      else if (element instanceof PsiMethodReferenceExpression) {
        // todo convert method ref to anonymous class
      }
    }

    @Override
    public @Nullable ModPlanEdit asModPlanEdit() {
      PsiElement element = getElement();
      if (!isSafeDelete() || element == null) return null;
      boolean isMethodUsage = myIsMethodUsage;
      return ModPlanEdit.update(element, copy -> toAnonymousClass(copy, isMethodUsage));
    }
  }

  private static class SafeDeleteExternalAnnotationsUsageInfo extends SafeDeleteReferenceUsageInfo implements ModPlanSafeDeleteUsage {

    SafeDeleteExternalAnnotationsUsageInfo(PsiElement referenceElement, PsiElement element) {
      super(element, referenceElement, true);
    }

    /**
     * Takes the new text of the annotations files from the command that removes all the external annotations of the element. An element
     * that no annotations file names has nothing to remove: its edit changes nothing.
     */
    @Override
    public @Nullable ModPlanEdit asModPlanEdit() {
      PsiElement referencedElement = findReferencedElement();
      if (referencedElement == null) return null;
      Project project = referencedElement.getProject();
      List<ModUpdateFileText> updates = ContainerUtil.filterIsInstance(
        ModCommandAwareExternalAnnotationsManager.getInstance(project).deannotateModCommand(ownersOf(referencedElement)).unpack(),
        ModUpdateFileText.class);
      List<PsiFile> files = ContainerUtil.mapNotNull(updates, update -> PsiManager.getInstance(project).findFile(update.file()));
      if (files.size() != updates.size() || files.isEmpty()) return ModPlanEdit.update(referencedElement, _ -> {});
      return ModPlanEdit.update(files, copies -> {
        for (int i = 0; i < copies.size(); i++) {
          Document document = ((PsiFile)copies.get(i)).getViewProvider().getDocument();
          if (document != null) {
            document.setText(updates.get(i).newText());
            PsiDocumentManager.getInstance(project).commitDocument(document);
          }
        }
      });
    }

    @Override
    public void deleteElement() {
      PsiElement referencedElement = findReferencedElement();
      if (referencedElement == null || !referencedElement.isValid()) return;
      List<PsiModifierListOwner> list = ownersOf(referencedElement);
      ModCommandAwareExternalAnnotationsManager annotationsManager =
        ModCommandAwareExternalAnnotationsManager.getInstance(referencedElement.getProject());
      ModCommandExecutor.executeInteractively(
        ActionContext.from(null, referencedElement.getContainingFile()),
        JavaBundle.message("update.external.annotations"),
        null,
        () -> annotationsManager.deannotateModCommand(list));
    }

    /** @return the element, and the parameters of a method, as the deletion removes their external annotations */
    private static @NotNull List<PsiModifierListOwner> ownersOf(@NotNull PsiElement referencedElement) {
      if (referencedElement instanceof PsiMethod method) {
        return StreamEx.<PsiModifierListOwner>of(method.getParameterList().getParameters()).prepend(method).toList();
      }
      return List.of((PsiModifierListOwner)referencedElement);
    }
  }
}
