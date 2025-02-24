// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.SafeDeleteFix;
import com.intellij.codeInsight.generation.GetterSetterPrototypeProvider;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.FindSuperElementsHelper;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ContractConverter;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.changeSignature.inCallers.AbstractJavaMemberCallerChooser;
import com.intellij.refactoring.move.moveClassesOrPackages.ModuleInfoUsageDetector;
import com.intellij.refactoring.safeDelete.usageInfo.*;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.tree.TreeUtil;
import com.siyeh.ig.style.LambdaCanBeReplacedWithAnonymousInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
           element instanceof PsiPackage;
  }

  @Override
  public @Nullable NonCodeUsageSearchInfo findUsages(@NotNull PsiElement element,
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
    else if (element instanceof PsiLocalVariable) {
      for (PsiReference reference : ReferencesSearch.search(element).asIterable()) {
        PsiReferenceExpression referencedElement = (PsiReferenceExpression)reference.getElement();
        PsiElement statementOrExprInList = PsiTreeUtil.getParentOfType(referencedElement, PsiStatement.class);
        if (statementOrExprInList instanceof PsiExpressionListStatement) {
          PsiExpressionList expressionList = ((PsiExpressionListStatement)statementOrExprInList).getExpressionList();
          if (expressionList != null) {
            statementOrExprInList = PsiTreeUtil.findPrevParent(expressionList, referencedElement);
          }
        }

        boolean isSafeToDelete = PsiUtil.isAccessedForWriting(referencedElement);
        boolean hasSideEffects = false;
        if (PsiUtil.isOnAssignmentLeftHand(referencedElement)) {
          PsiExpression rhs = ((PsiAssignmentExpression)referencedElement.getParent()).getRExpression();
          hasSideEffects = RemoveUnusedVariableUtil.checkSideEffects(rhs, (PsiLocalVariable)element, new ArrayList<>());
        }
        usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(statementOrExprInList, element, isSafeToDelete && !hasSideEffects));
      }
    }
    return new NonCodeUsageSearchInfo(insideDeletedCondition, element);
  }

  @Override
  public @Nullable Collection<? extends PsiElement> getElementsToSearch(@NotNull PsiElement element,
                                                                        @Nullable Module module,
                                                                        @NotNull Collection<? extends PsiElement> allElementsToDelete) {
    Project project = element.getProject();
    if (element instanceof PsiPackage aPackage && module != null) {
      PsiDirectory[] directories = aPackage.getDirectories(module.getModuleScope());
      if (directories.length == 0) return null;
      return Arrays.asList(directories);
    }
    if (element instanceof PsiMethod method) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        return Collections.singletonList(element);
      }
      PsiMethod[] methods = SuperMethodWarningUtil.checkSuperMethods(method, allElementsToDelete);
      if (methods.length == 0) return null;
      ArrayList<PsiMethod> psiMethods = new ArrayList<>(Arrays.asList(methods));
      psiMethods.add(method);
      return psiMethods;
    }
    if (element instanceof PsiParameter param && param.getDeclarationScope() instanceof PsiMethod method) {
      Set<PsiElement> parametersToDelete = new HashSet<>();
      parametersToDelete.add(element);
      int parameterIndex = method.getParameterList().getParameterIndex(param);
      List<PsiMethod> superMethods = new ArrayList<>(Arrays.asList(method.findDeepestSuperMethods()));
      if (superMethods.isEmpty()) {
        superMethods.add(method);
      }
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ReadAction.run(() -> {
        ContainerUtil.addAllNotNull(superMethods, FindSuperElementsHelper.getSiblingInheritedViaSubClass(method));
        for (PsiMethod superMethod : superMethods) {
          parametersToDelete.add(superMethod.getParameterList().getParameters()[parameterIndex]);
          OverridingMethodsSearch.search(superMethod).forEach(overrider -> {
            parametersToDelete.add(overrider.getParameterList().getParameters()[parameterIndex].getNavigationElement());
            return true;
          });
        }
      }), JavaRefactoringBundle.message("progress.title.collect.hierarchy", param.getName()), true, project)) {
        return null;
      }

      if (parametersToDelete.size() > 1 && !ApplicationManager.getApplication().isUnitTestMode()) {
        String message = JavaRefactoringBundle.message("0.is.a.part.of.method.hierarchy.do.you.want.to.delete.multiple.parameters",
                                                       UsageViewUtil.getLongName(method));
        int result = Messages.showYesNoCancelDialog(project, message, SafeDeleteHandler.getRefactoringName(),
                                                    Messages.getQuestionIcon());
        if (result == Messages.CANCEL) return null;
        if (result == Messages.NO) return Collections.singletonList(element);
      }
      return parametersToDelete;
    }
    if (element instanceof PsiTypeParameter typeParameter) {
      PsiTypeParameterListOwner owner = typeParameter.getOwner();
      if (owner instanceof PsiMethod && !owner.hasModifierProperty(PsiModifier.STATIC)) {
        PsiTypeParameterList typeParameterList = owner.getTypeParameterList();
        if (typeParameterList != null) {
          int index = typeParameterList.getTypeParameterIndex((PsiTypeParameter)element);
          if (index >= 0) {
            ArrayList<PsiTypeParameter> overriders = new ArrayList<>();
            overriders.add((PsiTypeParameter)element);
            OverridingMethodsSearch.search((PsiMethod)owner).forEach(overrider -> {
              PsiTypeParameter[] typeParameters = overrider.getTypeParameters();
              if (index < typeParameters.length) {
                overriders.add(typeParameters[index]);
              }
              return true;
            });
            if (overriders.size() > 1 && !ApplicationManager.getApplication().isUnitTestMode()) {
              String message =
                JavaRefactoringBundle.message("0.is.a.part.of.method.hierarchy.do.you.want.to.delete.multiple.type.parameters",
                                              UsageViewUtil.getLongName(owner));
              int result = ApplicationManager.getApplication().isUnitTestMode()
                           ? Messages.YES
                           : Messages.showYesNoCancelDialog(project, message, SafeDeleteHandler.getRefactoringName(),
                                                            Messages.getQuestionIcon());
              if (result == Messages.CANCEL) return null;
              if (result == Messages.NO) return Collections.singletonList(element);
            }
            return overriders;
          }
        }
      }
    }
    return Collections.singletonList(element);
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
        overridingMethods.add(info.getOverridingMethod());
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
  public Collection<PsiElement> getAdditionalElementsToDelete(@NotNull PsiElement element,
                                                              @NotNull Collection<? extends PsiElement> allElementsToDelete,
                                                              boolean askUser) {
    if (element instanceof PsiRecordComponent component) {
      PsiMethod method = JavaPsiRecordUtil.getAccessorForRecordComponent(component);
      List<PsiElement> additional = new ArrayList<>();
      if (method != null && !(method instanceof SyntheticElement) && !allElementsToDelete.contains(method)) {
        additional.add(method);
      }
      PsiClass recordClass = component.getContainingClass();
      assert recordClass != null;
      PsiMethod constructor = JavaPsiRecordUtil.findCanonicalConstructor(recordClass);
      if (constructor == null || constructor instanceof SyntheticElement) return additional; 
      PsiRecordHeader header = recordClass.getRecordHeader();
      assert header != null;
      int index = ArrayUtil.indexOf(header.getRecordComponents(), component);
      if (index < 0) return additional;
      PsiParameter parameter = constructor.getParameterList().getParameter(index);
      if (parameter != null) {
        additional.add(parameter);
      }
      return additional;
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
        if (askUser && (getters != null || setter != null)) {
          String title = RefactoringBundle.message("delete.title");
          String message =
            RefactoringMessageUtil.getGetterSetterMessage(field.getName(), title, getters != null ? getters[0] : null, setter);
          if (!ApplicationManager.getApplication().isUnitTestMode() &&
              Messages.showYesNoDialog(project, message, RefactoringBundle.message("safe.delete.title"),
                                       Messages.getQuestionIcon()) != Messages.YES) {
            getters = null;
            setter = null;
          }
        }
        List<PsiElement> elements = new ArrayList<>();
        if (setter != null) elements.add(setter);
        if (getters != null) Collections.addAll(elements, getters);
        return elements;
      }
    }
    return null;
  }

  @Override
  public Collection<@DialogMessage String> findConflicts(@NotNull PsiElement element,
                                                         PsiElement @NotNull [] elements,
                                                         UsageInfo @NotNull [] usages) {
    final PsiMethod method;
    if (element instanceof PsiMethod m) method = m;
    else if (element instanceof PsiParameter p) method = (p.getDeclarationScope() instanceof PsiMethod m) ? m : null;
    else method = null;
    String methodRefFound = null;
    if (method != null) {
      for (UsageInfo usage : usages) {
        if (usage.getElement() instanceof PsiMethodReferenceExpression ref && method.equals(ref.resolve())) {
          methodRefFound = JavaRefactoringBundle.message("expand.method.reference.warning");
          break;
        }
      }
    }
    if (methodRefFound != null) {
      Collection<String> result = new ArrayList<>();
      result.add(methodRefFound);
      Collection<String> conflicts = super.findConflicts(element, elements, usages);
      if (conflicts != null) {
        result.addAll(conflicts);
      }
      return result;
    }
    return super.findConflicts(element, elements, usages);
  }

  @Override
  public Collection<@DialogMessage String> findConflicts(@NotNull PsiElement element, PsiElement @NotNull [] allElementsToDelete) {
    if (element instanceof PsiMethod method) {
      PsiClass containingClass = method.getContainingClass();

      if (containingClass != null && !containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        PsiMethod[] superMethods = method.findSuperMethods();
        for (PsiMethod superMethod : superMethods) {
          if (isInside(superMethod, allElementsToDelete)) continue;
          if (superMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
            String message = JavaRefactoringBundle.message("0.implements.1", RefactoringUIUtil.getDescription(element, true),
                                                           RefactoringUIUtil.getDescription(superMethod, true));
            return Collections.singletonList(message);
          }
        }
      }
    }
    else if (element instanceof PsiParameter parameter) {
      PsiElement scope = parameter.getDeclarationScope();
      if (scope instanceof PsiMethod method) {
        MultiMap<PsiElement, String> conflicts = new MultiMap<>();
        collectMethodConflicts(conflicts, method, parameter);
        return conflicts.values();
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
            return findConflicts(parameter, allElementsToDelete); 
          }
        }
      }
    }
    return null;
  }

  @Override
  public UsageInfo @Nullable [] preprocessUsages(@NotNull Project project, UsageInfo @NotNull [] usages) {
    List<UsageInfo> result = new ArrayList<>();
    List<UsageInfo> overridingMethods = new ArrayList<>();
    List<SafeDeleteParameterCallHierarchyUsageInfo> delegatingParams = new ArrayList<>();
    List<SafeDeleteMemberCalleeUsageInfo> calleesSafeToDelete = new ArrayList<>();
    for (UsageInfo usage : usages) {
      if (usage.isNonCodeUsage) {
        if (usage instanceof SafeDeleteUsageInfo info) {
          PsiElement element = info.getReferencedElement();
          if (element instanceof PsiModifierListOwner) {
            ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(element.getProject());
            List<PsiFile> annotationsFiles = annotationsManager.findExternalAnnotationsFiles((PsiModifierListOwner)element);
            if (annotationsFiles != null && annotationsFiles.contains(usage.getFile())) {
              result.add(new SafeDeleteExternalAnnotationsUsageInfo(element, usage.getElement()));
              continue;
            }
          }
        }
        result.add(usage);
      }
      else if (usage instanceof SafeDeleteMemberCalleeUsageInfo info) {
        calleesSafeToDelete.add(info);
      }
      else if (usage instanceof SafeDeleteOverridingMethodUsageInfo) {
        overridingMethods.add(usage);
      }
      else if (usage instanceof SafeDeleteParameterCallHierarchyUsageInfo info) {
        delegatingParams.add(info);
      }
      else if (usage instanceof SafeDeleteAnnotation info) {
        result.add(new SafeDeleteAnnotation((PsiAnnotation)usage.getElement(), info.getReferencedElement(), true));
      }
      else {
        result.add(usage);
      }
    }

    if (!overridingMethods.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        result.addAll(overridingMethods);
      }
      else {
        OverridingMethodsDialog dialog = new OverridingMethodsDialog(project, overridingMethods);
        if (!dialog.showAndGet()) {
          return null;
        }
        ArrayList<UsageInfo> selected = dialog.getSelected();
        Set<UsageInfo> unselected = new HashSet<>(overridingMethods);
        for (UsageInfo usageInfo : selected) {
          unselected.remove(usageInfo);
        }

        if (!unselected.isEmpty()) {
          List<PsiElement> unselectedMethods =
            ContainerUtil.map(unselected, info -> ((SafeDeleteOverridingMethodUsageInfo)info).getOverridingMethod());

          result.removeIf(info -> info instanceof SafeDeleteOverrideAnnotation anno &&
                                  !allSuperMethodsSelectedToDelete(unselectedMethods, anno.getMethod()));
        }

        result.addAll(selected);
      }
    }

    if (!delegatingParams.isEmpty()) {
      SafeDeleteParameterCallHierarchyUsageInfo parameterHierarchyUsageInfo = delegatingParams.get(0);
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        result.addAll(delegatingParams);
      }
      else {
        PsiMethod method = parameterHierarchyUsageInfo.getCalledMethod();
        PsiParameter parameter = parameterHierarchyUsageInfo.getReferencedElement();
        int parameterIndex = method.getParameterList().getParameterIndex(parameter);
        AbstractJavaMemberCallerChooser<?> chooser = new SafeDeleteJavaCallerChooser(method, project, result) {
          @Override
          protected @NotNull List<SafeDeleteParameterCallHierarchyUsageInfo> getTopLevelItems() {
            return delegatingParams;
          }

          @Override
          protected int getParameterIdx() {
            return parameterIndex;
          }

          @Override
          protected PsiParameter getParameterInCaller(PsiMethod called, int paramIdx, PsiMethod caller) {
            return delegatingParams.stream()
              .filter(usage -> caller.equals(usage.getCallerMethod()))
              .map(usage -> usage.getParameterInCaller())
              .findFirst()
              .orElse(super.getParameterInCaller(called, paramIdx, caller));
          }
        };
        TreeUtil.expand(chooser.getTree(), 2);
        if (!chooser.showAndGet()) {
          return null;
        }
      }
    }

    if (!calleesSafeToDelete.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        result.addAll(calleesSafeToDelete);
      }
      else {
        PsiMember member = calleesSafeToDelete.get(0).getCallerMember();
        List<UsageInfo> list = new ArrayList<>();
        SafeDeleteJavaCalleeChooser chooser = new SafeDeleteJavaCalleeChooser(member, project, list) {
          @Override
          protected @NotNull List<SafeDeleteMemberCalleeUsageInfo> getTopLevelItems() {
            return calleesSafeToDelete;
          }
        };
        TreeUtil.expand(chooser.getTree(), 2);
        if (!chooser.showAndGet()) {
          return null;
        }
        result.addAll(list);
        List<PsiElement> methodsToDelete = new ArrayList<>();
        for (UsageInfo info : list) {
          methodsToDelete.add(info.getElement());
        }
        methodsToDelete.add(member);
        Condition<PsiElement> insideDeletedCondition = getUsageInsideDeletedFilter(methodsToDelete.toArray(PsiElement.EMPTY_ARRAY));
        for (UsageInfo info : list) {
          PsiElement psi = info.getElement();
          JavaRefactoringSettings refactoringSettings = JavaRefactoringSettings.getInstance();
          boolean searchNonJava = psi instanceof PsiMethod
                                  ? refactoringSettings.RENAME_SEARCH_FOR_TEXT_FOR_METHOD
                                  : refactoringSettings.RENAME_SEARCH_FOR_TEXT_FOR_FIELD;
          boolean searchInCommentsAndStrings = psi instanceof PsiMethod
                                               ? refactoringSettings.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD
                                               : refactoringSettings.RENAME_SEARCH_IN_COMMENTS_FOR_FIELD;
          SafeDeleteProcessor.addNonCodeUsages(psi, GlobalSearchScope.projectScope(project), result, insideDeletedCondition,
                                               searchNonJava, searchInCommentsAndStrings);
        }
      }
    }

    return result.toArray(UsageInfo.EMPTY_ARRAY);
  }

  private static boolean allSuperMethodsSelectedToDelete(List<PsiElement> unselectedMethods, PsiMethod method) {
    ArrayList<PsiMethod> superMethods = new ArrayList<>(Arrays.asList(method.findSuperMethods()));
    superMethods.retainAll(unselectedMethods);
    return superMethods.isEmpty();
  }

  @Override
  public void prepareForDeletion(@NotNull PsiElement element) {
    if (element instanceof PsiVariable var) {
      var.normalizeDeclaration();
    }
    if (element instanceof PsiParameter parameter && element.getParent() instanceof PsiParameterList parameterList) {
      PsiMethod method = ObjectUtils.tryCast(parameterList.getParent(), PsiMethod.class);
      if (method != null) {
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
          catch (ContractConverter.ContractConversionException ignored) { }
        }
      }
    }
  }

  @Override
  public boolean isToSearchInComments(PsiElement element) {
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
        if (parent instanceof PsiClassOwner) {
          PsiClass[] classes = ((PsiClassOwner)parent).getClasses();
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
        if (containingFile instanceof PsiClassOwner) {
          PsiClass[] classes = ((PsiClassOwner)containingFile).getClasses();
          sameFileWithSingleClass = classes.length == 1 &&
                                    classes[0] == psiClass &&
                                    element.getContainingFile() == containingFile;
        }

        boolean safeDelete = sameFileWithSingleClass || isInNonStaticImport(element);
        if (annotationType && parent instanceof PsiAnnotation) {
          usages.add(new SafeDeleteAnnotation((PsiAnnotation)parent, psiClass, safeDelete));
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
    for (PsiElement callee : SafeDeleteFix.computeReferencedCodeSafeToDelete(method)) {
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
        PsiKeyword.SUPER.equals(element.getText()) &&
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
        if (scope instanceof PsiMethod) {
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
          usages.add(createParameterCallHierarchyUsageInfo(setterPrototype, setterParameter, (PsiMethod)scope, parameter));
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
      int parameterIndex = method.getParameterList().getParameterIndex(parameter);
      PsiMethod methodCopy = (PsiMethod)method.copy();
      methodCopy.getParameterList().getParameters()[parameterIndex].delete();
      ConflictsUtil.checkMethodConflicts(containingClass, method, methodCopy, conflicts);
    }
  }

  private static class SafeDeleteFunctionalExpressionUsageInfo extends SafeDeleteReferenceUsageInfo {
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
      if (!myIsMethodUsage) return;
      PsiElement element = getElement();
      if (element instanceof PsiLambdaExpression lambda) {
        PsiAnonymousClass aClass = LambdaCanBeReplacedWithAnonymousInspection.doFix(getProject(), lambda);
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
  }

  private static class SafeDeleteExternalAnnotationsUsageInfo extends SafeDeleteReferenceUsageInfo {

    SafeDeleteExternalAnnotationsUsageInfo(PsiElement referenceElement, PsiElement element) {
      super(element, referenceElement, true);
    }

    @Override
    public void deleteElement() {
      PsiElement referencedElement = getReferencedElement();
      if (!referencedElement.isValid()) return;
      ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(referencedElement.getProject());
      PsiAnnotation[] externalAnnotations = annotationsManager.findExternalAnnotations((PsiModifierListOwner)referencedElement);
      for (PsiAnnotation annotation : externalAnnotations) {
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName == null) continue;
        annotationsManager.deannotate((PsiModifierListOwner)referencedElement, qualifiedName);
      }
    }
  }
}
