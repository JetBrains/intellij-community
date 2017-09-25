/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.refactoring.safeDelete;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableUtil;
import com.intellij.codeInsight.generation.GetterSetterPrototypeProvider;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.inCallers.AbstractJavaMemberCallerChooser;
import com.intellij.refactoring.safeDelete.usageInfo.*;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JavaSafeDeleteProcessor extends SafeDeleteProcessorDelegateBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.safeDelete.JavaSafeDeleteProcessor");

  public boolean handlesElement(final PsiElement element) {
    return element instanceof PsiClass || element instanceof PsiMethod ||
           element instanceof PsiField || element instanceof PsiParameter || element instanceof PsiLocalVariable || element instanceof PsiPackage;
  }

  @Nullable
  public NonCodeUsageSearchInfo findUsages(@NotNull final PsiElement element, @NotNull final PsiElement[] allElementsToDelete, @NotNull final List<UsageInfo> usages) {
    Condition<PsiElement> insideDeletedCondition = getUsageInsideDeletedFilter(allElementsToDelete);
    if (element instanceof PsiClass) {
      findClassUsages((PsiClass) element, allElementsToDelete, usages);
      if (element instanceof PsiTypeParameter) {
        findTypeParameterExternalUsages((PsiTypeParameter)element, usages);
      }
    }
    else if (element instanceof PsiMethod) {
      insideDeletedCondition = findMethodUsages((PsiMethod) element, allElementsToDelete, usages);
    }
    else if (element instanceof PsiField) {
      insideDeletedCondition = findFieldUsages((PsiField)element, usages, allElementsToDelete);
    }
    else if (element instanceof PsiParameter) {
      LOG.assertTrue(((PsiParameter) element).getDeclarationScope() instanceof PsiMethod);
      findParameterUsages((PsiParameter)element, usages);
    }
    else if (element instanceof PsiLocalVariable) {
      for (PsiReference reference : ReferencesSearch.search(element)) {
        PsiReferenceExpression referencedElement = (PsiReferenceExpression)reference.getElement();
        final PsiStatement statement = PsiTreeUtil.getParentOfType(referencedElement, PsiStatement.class);

        boolean isSafeToDelete = PsiUtil.isAccessedForWriting(referencedElement);
        boolean hasSideEffects = false;
        if (PsiUtil.isOnAssignmentLeftHand(referencedElement)) {
          hasSideEffects =
            RemoveUnusedVariableUtil
              .checkSideEffects(((PsiAssignmentExpression)referencedElement.getParent()).getRExpression(), ((PsiLocalVariable)element),
                                new ArrayList<>());
        }
        usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(statement, element, isSafeToDelete && !hasSideEffects));
      }
    }
    return new NonCodeUsageSearchInfo(insideDeletedCondition, element);
  }

  @Nullable
  @Override
  public Collection<? extends PsiElement> getElementsToSearch(@NotNull PsiElement element,
                                                              @Nullable Module module,
                                                              @NotNull Collection<PsiElement> allElementsToDelete) {
    Project project = element.getProject();
    if (element instanceof PsiPackage && module != null) {
      final PsiDirectory[] directories = ((PsiPackage)element).getDirectories(module.getModuleScope());
      if (directories.length == 0) return null;
      return Arrays.asList(directories);
    } else if (element instanceof PsiMethod) {
      final PsiMethod[] methods =
        SuperMethodWarningUtil.checkSuperMethods((PsiMethod)element, RefactoringBundle.message("to.delete.with.usage.search"),
                                                 allElementsToDelete);
      if (methods.length == 0) return null;
      final ArrayList<PsiMethod> psiMethods = new ArrayList<>(Arrays.asList(methods));
      psiMethods.add((PsiMethod)element);
      return psiMethods;
    }
    else if (element instanceof PsiParameter && ((PsiParameter) element).getDeclarationScope() instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) ((PsiParameter) element).getDeclarationScope();
      final Set<PsiParameter> parametersToDelete = new HashSet<>();
      parametersToDelete.add((PsiParameter) element);
      final int parameterIndex = method.getParameterList().getParameterIndex((PsiParameter) element);
      final List<PsiMethod> superMethods = new ArrayList<>(Arrays.asList(method.findDeepestSuperMethods()));
      if (superMethods.isEmpty()) {
        superMethods.add(method);
      }
      for (PsiMethod superMethod : superMethods) {
        parametersToDelete.add(superMethod.getParameterList().getParameters()[parameterIndex]);
        OverridingMethodsSearch.search(superMethod).forEach(overrider -> {
          parametersToDelete.add(overrider.getParameterList().getParameters()[parameterIndex]);
          return true;
        });
      }

      if (parametersToDelete.size() > 1 && !ApplicationManager.getApplication().isUnitTestMode()) {
        String message = RefactoringBundle.message("0.is.a.part.of.method.hierarchy.do.you.want.to.delete.multiple.parameters", UsageViewUtil.getLongName(method));
        int result = Messages.showYesNoCancelDialog(project, message, SafeDeleteHandler.REFACTORING_NAME,
                                               Messages.getQuestionIcon());
        if (result == Messages.CANCEL) return null;
        if (result == Messages.NO) return Collections.singletonList(element);
      }
      return parametersToDelete;
    }
    else if (element instanceof PsiTypeParameter) {
      final PsiTypeParameterListOwner owner = ((PsiTypeParameter)element).getOwner();
      if (owner instanceof PsiMethod && !owner.hasModifierProperty(PsiModifier.STATIC)) {
        final PsiTypeParameterList typeParameterList = owner.getTypeParameterList();
        if (typeParameterList != null) {
          final int index = typeParameterList.getTypeParameterIndex((PsiTypeParameter)element);
          if (index >= 0) {
            final ArrayList<PsiTypeParameter> overriders = new ArrayList<>();
            overriders.add((PsiTypeParameter)element);
            OverridingMethodsSearch.search((PsiMethod)owner).forEach(overrider -> {
              final PsiTypeParameter[] typeParameters = overrider.getTypeParameters();
              if (index < typeParameters.length) {
                overriders.add(typeParameters[index]);
              }
              return true;
            });
            if (overriders.size() > 1) {
              String message = RefactoringBundle.message("0.is.a.part.of.method.hierarchy.do.you.want.to.delete.multiple.type.parameters", UsageViewUtil.getLongName(owner));
              int result = ApplicationManager.getApplication().isUnitTestMode() 
                           ? Messages.YES :Messages.showYesNoCancelDialog(project, message, SafeDeleteHandler.REFACTORING_NAME, Messages.getQuestionIcon());
              if (result == Messages.CANCEL) return null;
              if (result == Messages.YES) return overriders;
            }
          }
        }
      }
    }
    
    return Collections.singletonList(element);
  }

  @Override
  public UsageView showUsages(UsageInfo[] usages, UsageViewPresentation presentation, UsageViewManager manager, PsiElement[] elements) {
    final List<PsiElement> overridingMethods = new ArrayList<>();
    final List<UsageInfo> others = new ArrayList<>();
    for (UsageInfo usage : usages) {
      if (usage instanceof SafeDeleteOverridingMethodUsageInfo) {
        overridingMethods.add(((SafeDeleteOverridingMethodUsageInfo)usage).getOverridingMethod());
      } else {
        others.add(usage);
      }
    }

    UsageTarget[] targets = new UsageTarget[elements.length + overridingMethods.size()];
    for (int i = 0; i < targets.length; i++) {
      if (i < elements.length) {
        targets[i] = new PsiElement2UsageTargetAdapter(elements[i]);
      } else {
        targets[i] = new PsiElement2UsageTargetAdapter(overridingMethods.get(i - elements.length));
      }
    }

    return manager.showUsages(targets,
                              UsageInfoToUsageConverter.convert(elements,
                                                                others.toArray(new UsageInfo[others.size()])),
                              presentation
    );
  }

  public Collection<PsiElement> getAdditionalElementsToDelete(@NotNull final PsiElement element,
                                                              @NotNull final Collection<PsiElement> allElementsToDelete,
                                                              final boolean askUser) {
    if (element instanceof PsiField) {
      PsiField field = (PsiField)element;
      final Project project = element.getProject();
      String propertyName = JavaCodeStyleManager.getInstance(project).variableNameToPropertyName(field.getName(), VariableKind.FIELD);

      PsiClass aClass = field.getContainingClass();
      if (aClass != null) {
        boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
        PsiMethod[] getters = GetterSetterPrototypeProvider.findGetters(aClass, propertyName, isStatic);
        if (getters != null) {
          final List<PsiMethod> validGetters = new ArrayList<>(1);
          for (PsiMethod getter : getters) {
            if (!allElementsToDelete.contains(getter) && (getter != null && getter.isPhysical())) {
              validGetters.add(getter);
            }
          }
          getters = validGetters.isEmpty() ? null : validGetters.toArray(new PsiMethod[validGetters.size()]);
        }

        PsiMethod setter = PropertyUtilBase.findPropertySetter(aClass, propertyName, isStatic, false);
        if (allElementsToDelete.contains(setter) || setter != null && !setter.isPhysical()) setter = null;
        if (askUser && (getters != null || setter != null)) {
          final String message =
            RefactoringMessageUtil.getGetterSetterMessage(field.getName(), RefactoringBundle.message("delete.title"), getters != null ? getters[0] : null, setter);
          if (!ApplicationManager.getApplication().isUnitTestMode() && Messages.showYesNoDialog(project, message, RefactoringBundle.message("safe.delete.title"), Messages.getQuestionIcon()) != Messages.YES) {
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
  public Collection<String> findConflicts(PsiElement element, PsiElement[] elements, UsageInfo[] usages) {
    String methodRefFound = null;
    if (element instanceof PsiMethod || element instanceof PsiParameter) {
      PsiMethod method;
      if (element instanceof PsiMethod) {
        method = (PsiMethod)element;
      }
      else {
        PsiElement declarationScope = ((PsiParameter)element).getDeclarationScope();
        method = declarationScope instanceof PsiMethod ? (PsiMethod)declarationScope : null;
      }
      if (method != null) {
        for (UsageInfo usage : usages) {
          final PsiElement refElement = usage.getElement();
          if (refElement instanceof PsiMethodReferenceExpression && method.equals(((PsiMethodReferenceExpression)refElement).resolve())) {
            methodRefFound = RefactoringBundle.message("expand.method.reference.warning");
            break;
          }
        }
      }
    }
    if (methodRefFound != null) {
      Collection<String> result = new ArrayList<>();
      result.add(methodRefFound);
      final Collection<String> conflicts = super.findConflicts(element, elements, usages);
      if (conflicts != null) {
        result.addAll(conflicts);
      }
      return result;
    }
    return super.findConflicts(element, elements, usages);
  }

  public Collection<String> findConflicts(@NotNull final PsiElement element, @NotNull final PsiElement[] allElementsToDelete) {
    if (element instanceof PsiMethod) {
      final PsiClass containingClass = ((PsiMethod)element).getContainingClass();

      if (containingClass != null && !containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        final PsiMethod[] superMethods = ((PsiMethod) element).findSuperMethods();
        for (PsiMethod superMethod : superMethods) {
          if (isInside(superMethod, allElementsToDelete)) continue;
          if (superMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
            String message = RefactoringBundle.message("0.implements.1", RefactoringUIUtil.getDescription(element, true),
                                                       RefactoringUIUtil.getDescription(superMethod, true));
            return Collections.singletonList(message);
          }
        }
      }
    }
    else if (element instanceof PsiParameter) {
      final PsiElement scope = ((PsiParameter)element).getDeclarationScope();
      if (scope instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)scope;
        final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
        collectMethodConflicts(conflicts, method, (PsiParameter)element);
        return (Collection<String>)conflicts.values();
      }
    }
    return null;
  }

  @Nullable
  public UsageInfo[] preprocessUsages(final Project project, final UsageInfo[] usages) {
    final ArrayList<UsageInfo> result = new ArrayList<>();
    final ArrayList<UsageInfo> overridingMethods = new ArrayList<>();
    final ArrayList<SafeDeleteParameterCallHierarchyUsageInfo> delegatingParams = new ArrayList<>();
    final ArrayList<SafeDeleteMemberCalleeUsageInfo> calleesSafeToDelete = new ArrayList<>();
    for (UsageInfo usage : usages) {
      if (usage.isNonCodeUsage) {
        if (usage instanceof SafeDeleteUsageInfo) {
          PsiElement element = ((SafeDeleteUsageInfo)usage).getReferencedElement();
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
      else if (usage instanceof SafeDeleteMemberCalleeUsageInfo) {
        calleesSafeToDelete.add((SafeDeleteMemberCalleeUsageInfo)usage);
      }
      else if (usage instanceof SafeDeleteOverridingMethodUsageInfo) {
        overridingMethods.add(usage);
      }
      else if (usage instanceof SafeDeleteParameterCallHierarchyUsageInfo) {
        delegatingParams.add((SafeDeleteParameterCallHierarchyUsageInfo)usage);
      }
      else if (usage instanceof SafeDeleteAnnotation) {
        result.add(new SafeDeleteAnnotation((PsiAnnotation)usage.getElement(), ((SafeDeleteAnnotation)usage).getReferencedElement(), true));
      }
      else {
        result.add(usage);
      }
    }

    if(!overridingMethods.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        result.addAll(overridingMethods);
      }
      else {
        OverridingMethodsDialog dialog = new OverridingMethodsDialog(project, overridingMethods);
        if (!dialog.showAndGet()) {
          return null;
        }
        final ArrayList<UsageInfo> selected = dialog.getSelected();
        final Set<UsageInfo> unselected = new HashSet<>(overridingMethods);
        unselected.removeAll(selected);

        if (!unselected.isEmpty()) {
          final List<PsiMethod> unselectedMethods = ContainerUtil.map(unselected, info -> ((SafeDeleteOverridingMethodUsageInfo)info).getOverridingMethod());

          for (Iterator<UsageInfo> iterator = result.iterator(); iterator.hasNext(); ) {
            final UsageInfo info = iterator.next();
            if (info instanceof SafeDeleteOverrideAnnotation &&
                !allSuperMethodsSelectedToDelete(unselectedMethods, ((SafeDeleteOverrideAnnotation)info).getMethod())) {
              iterator.remove();
            }
          }
        }

        result.addAll(selected);
      }
    }

    if (!delegatingParams.isEmpty()) {
      final SafeDeleteParameterCallHierarchyUsageInfo parameterHierarchyUsageInfo = delegatingParams.get(0);
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        result.addAll(delegatingParams);
      } else {
        final PsiMethod method = parameterHierarchyUsageInfo.getCalledMethod();
        final PsiParameter parameter = parameterHierarchyUsageInfo.getReferencedElement();
        final int parameterIndex = method.getParameterList().getParameterIndex(parameter);
        final AbstractJavaMemberCallerChooser chooser = new SafeDeleteJavaCallerChooser(method, project, result) {
          @Override
          protected ArrayList<SafeDeleteParameterCallHierarchyUsageInfo> getTopLevelItems() {
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
        final PsiMember member = calleesSafeToDelete.get(0).getCallerMember();
        final ArrayList<UsageInfo> list = new ArrayList<>();
        AbstractJavaMemberCallerChooser chooser = new SafeDeleteJavaCalleeChooser(member, project, list) {
          @Override
          protected ArrayList<SafeDeleteMemberCalleeUsageInfo> getTopLevelItems() {
            return calleesSafeToDelete;
          }
        };
        TreeUtil.expand(chooser.getTree(), 2);
        if (!chooser.showAndGet()) {
          return null;
        }
        result.addAll(list);
        final List<PsiElement> methodsToDelete = new ArrayList<>();
        for (UsageInfo info : list) {
          methodsToDelete.add(info.getElement());
        }
        methodsToDelete.add(member);
        final Condition<PsiElement> insideDeletedCondition = getUsageInsideDeletedFilter(methodsToDelete.toArray(new PsiElement[methodsToDelete.size()]));
        for (UsageInfo info : list) {
          PsiElement psi = info.getElement();
          JavaRefactoringSettings refactoringSettings = JavaRefactoringSettings.getInstance();
          SafeDeleteProcessor.addNonCodeUsages(psi, result, insideDeletedCondition,
                                               psi instanceof PsiMethod ? refactoringSettings.RENAME_SEARCH_FOR_TEXT_FOR_METHOD : refactoringSettings.RENAME_SEARCH_FOR_TEXT_FOR_FIELD,
                                               psi instanceof PsiMethod ? refactoringSettings.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD : refactoringSettings.RENAME_SEARCH_IN_COMMENTS_FOR_FIELD );
        }
      }
    }

    return result.toArray(new UsageInfo[result.size()]);
  }

  private static boolean allSuperMethodsSelectedToDelete(List<PsiMethod> unselectedMethods, PsiMethod method) {
    final ArrayList<PsiMethod> superMethods = new ArrayList<>(Arrays.asList(method.findSuperMethods()));
    superMethods.retainAll(unselectedMethods);
    return superMethods.isEmpty();
  }

  public void prepareForDeletion(final PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiVariable) {
      ((PsiVariable)element).normalizeDeclaration();
    }
  }

  @Override
  public boolean isToSearchInComments(PsiElement element) {
    if (element instanceof PsiClass) {
      return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_CLASS;
    }
    else if (element instanceof PsiMethod) {
      return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_METHOD;
    }
    else if (element instanceof PsiVariable) {
      return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE;
    }
    else if (element instanceof PsiPackage) {
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
    else if (element instanceof PsiMethod) {
      return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_METHOD;
    }
    else if (element instanceof PsiVariable) {
      return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE;
    }
    else if (element instanceof PsiPackage) {
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

  public static Condition<PsiElement> getUsageInsideDeletedFilter(final PsiElement[] allElementsToDelete) {
    return usage -> !(usage instanceof PsiFile) && isInside(usage, allElementsToDelete);
  }

  private static void findClassUsages(final PsiClass psiClass, final PsiElement[] allElementsToDelete, final List<UsageInfo> usages) {
    final boolean justPrivates = containsOnlyPrivates(psiClass);
    final String qualifiedName = psiClass.getQualifiedName();
    final boolean annotationType = psiClass.isAnnotationType() && qualifiedName != null;

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
      final PsiElement element = reference.getElement();

      if (!isInside(element, topElementsToDelete)) {
        PsiElement parent = element.getParent();
        if (parent instanceof PsiReferenceList) {
          final PsiElement pparent = parent.getParent();
          if (pparent instanceof PsiClass && element instanceof PsiJavaCodeReferenceElement) {
            final PsiClass inheritor = (PsiClass) pparent;
            //If psiClass contains only private members, then it is safe to remove it and change inheritor's extends/implements accordingly
            if (justPrivates) {
              if (parent.equals(inheritor.getExtendsList()) || parent.equals(inheritor.getImplementsList())) {
                usages.add(new SafeDeleteExtendsClassUsageInfo((PsiJavaCodeReferenceElement)element, psiClass, inheritor));
                return true;
              }
            }
          }
        }
        LOG.assertTrue(element.getTextRange() != null);
        final PsiFile containingFile = psiClass.getContainingFile();
        boolean sameFileWithSingleClass = false;
        if (containingFile instanceof PsiClassOwner) {
          final PsiClass[] classes = ((PsiClassOwner)containingFile).getClasses();
          sameFileWithSingleClass = classes.length == 1 &&
                                    classes[0] == psiClass &&
                                    element.getContainingFile() == containingFile;
        }

        final boolean safeDelete = sameFileWithSingleClass || isInNonStaticImport(element);
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

  private static boolean containsOnlyPrivates(final PsiClass aClass) {
    final PsiField[] fields = aClass.getFields();
    for (PsiField field : fields) {
      if (!field.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    }

    final PsiMethod[] methods = aClass.getMethods();
    for (PsiMethod method : methods) {
      if (!method.hasModifierProperty(PsiModifier.PRIVATE)) {
        if (method.isConstructor()) { //skip non-private constructors with call to super only
          final PsiCodeBlock body = method.getBody();
          if (body != null) {
            final PsiStatement[] statements = body.getStatements();
            if (statements.length == 0) continue;
            if (statements.length == 1 && statements[0] instanceof PsiExpressionStatement) {
              final PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
              if (expression instanceof PsiMethodCallExpression) {
                PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)expression).getMethodExpression();
                if (methodExpression.getText().equals(PsiKeyword.SUPER)) {
                  continue;
                }
              }
            }
          }
        }
        return false;
      }
    }

    final PsiClass[] inners = aClass.getInnerClasses();
    for (PsiClass inner : inners) {
      if (!inner.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    }

    return true;
  }

  private static void findTypeParameterExternalUsages(final PsiTypeParameter typeParameter, final Collection<UsageInfo> usages) {
    PsiTypeParameterListOwner owner = typeParameter.getOwner();
    if (owner != null) {
      final PsiTypeParameterList parameterList = owner.getTypeParameterList();
      if (parameterList != null) {
        final int paramsCount = parameterList.getTypeParameters().length;
        final int index = parameterList.getTypeParameterIndex(typeParameter);

        ReferencesSearch.search(owner).forEach(reference -> {
          if (reference instanceof PsiJavaCodeReferenceElement) {
            final PsiReferenceParameterList parameterList1 = ((PsiJavaCodeReferenceElement)reference).getParameterList();
            if (parameterList1 != null) {
              PsiTypeElement[] typeArgs = parameterList1.getTypeParameterElements();
              if (typeArgs.length > index) {
                if (typeArgs.length == 1 && paramsCount > 1 && typeArgs[0].getType() instanceof PsiDiamondType) return true;
                usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(typeArgs[index], typeParameter, true));
              }
            }
          }
          return true;
        });
      }
    }
  }

  @Nullable
  private static Condition<PsiElement> findMethodUsages(final PsiMethod psiMethod, final PsiElement[] allElementsToDelete, List<UsageInfo> usages) {
    final Collection<PsiReference> references = ReferencesSearch.search(psiMethod).findAll();

    if(psiMethod.isConstructor()) {
      return findConstructorUsages(psiMethod, references, usages, allElementsToDelete);
    }
    final PsiMethod[] overridingMethods =
            removeDeletedMethods(OverridingMethodsSearch.search(psiMethod).toArray(PsiMethod.EMPTY_ARRAY),
                                 allElementsToDelete);

    findFunctionalExpressions(usages, ArrayUtil.prepend(psiMethod, overridingMethods));

    final HashMap<PsiMethod, Collection<PsiReference>> methodToReferences = new HashMap<>();
    for (PsiMethod overridingMethod : overridingMethods) {
      final Collection<PsiReference> overridingReferences = ReferencesSearch.search(overridingMethod).findAll();
      methodToReferences.put(overridingMethod, overridingReferences);
    }
    final Set<PsiMethod> validOverriding =
      validateOverridingMethods(psiMethod, references, Arrays.asList(overridingMethods), methodToReferences, usages,
                                allElementsToDelete);
    for (PsiReference reference : references) {
      final PsiElement element = reference.getElement();
      if (!isInside(element, allElementsToDelete) && !isInside(element, validOverriding)) {
        usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, psiMethod, PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class) != null));
      }
    }

    appendCallees(psiMethod, usages);

    return usage -> {
      if(usage instanceof PsiFile) return false;
      return isInside(usage, allElementsToDelete) || isInside(usage,  validOverriding);
    };
  }

  private static void appendCallees(@NotNull PsiMember method, @NotNull List<UsageInfo> usages) {
    final List<PsiMember> calleesSafeToDelete = SafeDeleteJavaCalleeChooser.computeCalleesSafeToDelete(method);
    if (calleesSafeToDelete != null) {
      for (PsiMember callee : calleesSafeToDelete) {
        usages.add(new SafeDeleteMemberCalleeUsageInfo(callee, method));
      }
    }
  }

  private static void findFunctionalExpressions(final List<UsageInfo> usages, PsiMethod... methods) {
    for (PsiMethod method : methods) {
      final PsiClass containingClass = method.getContainingClass();
      FunctionalExpressionSearch.search(method).forEach(expression -> {
        usages.add(new SafeDeleteFunctionalExpressionUsageInfo(expression, containingClass));
        return true;
      });
    }
  }

  private static PsiMethod[] removeDeletedMethods(PsiMethod[] methods, final PsiElement[] allElementsToDelete) {
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
    return list.toArray(new PsiMethod[list.size()]);
  }

  @Nullable
  private static Condition<PsiElement> findConstructorUsages(PsiMethod constructor, Collection<PsiReference> originalReferences, List<UsageInfo> usages,
                                                             final PsiElement[] allElementsToDelete) {
    HashMap<PsiMethod, Collection<PsiReference>> constructorsToRefs = new HashMap<>();
    HashSet<PsiMethod> newConstructors = new HashSet<>();
    if (isTheOnlyEmptyDefaultConstructor(constructor)) return null;

    newConstructors.add(constructor);
    constructorsToRefs.put(constructor, originalReferences);
    HashSet<PsiMethod> passConstructors = new HashSet<>();
    do {
      passConstructors.clear();
      for (PsiMethod method : newConstructors) {
        final Collection<PsiReference> references = constructorsToRefs.get(method);
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

    final Set<PsiMethod> validOverriding =
            validateOverridingMethods(constructor, originalReferences, constructorsToRefs.keySet(), constructorsToRefs, usages,
                                      allElementsToDelete);

    return usage -> {
      if(usage instanceof PsiFile) return false;
      return isInside(usage, allElementsToDelete) || isInside(usage, validOverriding);
    };
  }

  private static boolean isTheOnlyEmptyDefaultConstructor(final PsiMethod constructor) {
    if (constructor.getParameterList().getParameters().length > 0) return false;
    final PsiCodeBlock body = constructor.getBody();
    if (body != null && body.getStatements().length > 0) return false;
    return constructor.getContainingClass().getConstructors().length == 1;
  }

  private static Set<PsiMethod> validateOverridingMethods(PsiMethod originalMethod, final Collection<PsiReference> originalReferences,
                                                   Collection<PsiMethod> overridingMethods, HashMap<PsiMethod, Collection<PsiReference>> methodToReferences,
                                                   List<UsageInfo> usages,
                                                   final PsiElement[] allElementsToDelete) {
    Set<PsiMethod> validOverriding = new LinkedHashSet<>(overridingMethods);
    Set<PsiMethod> multipleInterfaceImplementations = new HashSet<>();
    boolean anyNewBadRefs;
    do {
      anyNewBadRefs = false;
      for (PsiMethod overridingMethod : overridingMethods) {
        if (validOverriding.contains(overridingMethod)) {
          final Collection<PsiReference> overridingReferences = methodToReferences.get(overridingMethod);
          boolean anyOverridingRefs = false;
          for (final PsiReference overridingReference : overridingReferences) {
            final PsiElement element = overridingReference.getElement();
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
              final PsiElement element = reference.getElement();
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
      if (!validOverriding.contains(method) &&
          !multipleInterfaceImplementations.contains(method) &&
          canBePrivate(method, methodToReferences.get(method), validOverriding, allElementsToDelete)) {
        usages.add(new SafeDeletePrivatizeMethod(method, originalMethod));
      } else {
        usages.add(new SafeDeleteOverrideAnnotation(method, originalMethod));
      }
    }
    return validOverriding;
  }

  private static boolean isMultipleInterfacesImplementation(final PsiMethod method,
                                                            PsiMethod originalMethod,
                                                            final PsiElement[] allElementsToDelete) {
    final PsiMethod[] methods = method.findSuperMethods();
    for(PsiMethod superMethod: methods) {
      if (ArrayUtil.find(allElementsToDelete, superMethod) < 0 && !MethodSignatureUtil.isSuperMethod(originalMethod, superMethod)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static PsiMethod getOverridingConstructorOfSuperCall(final PsiElement element) {
    if(element instanceof PsiReferenceExpression && "super".equals(element.getText())) {
      PsiElement parent = element.getParent();
      if(parent instanceof PsiMethodCallExpression) {
        parent = parent.getParent();
        if(parent instanceof PsiExpressionStatement) {
          parent = parent.getParent();
          if(parent instanceof PsiCodeBlock) {
            parent = parent.getParent();
            if(parent instanceof PsiMethod && ((PsiMethod) parent).isConstructor()) {
              return (PsiMethod) parent;
            }
          }
        }
      }
    }
    return null;
  }

  private static boolean canBePrivate(PsiMethod method, Collection<PsiReference> references, Collection<? extends PsiElement> deleted,
                               final PsiElement[] allElementsToDelete) {
    final PsiClass containingClass = method.getContainingClass();
    if(containingClass == null) {
      return false;
    }

    PsiManager manager = method.getManager();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    final PsiElementFactory factory = facade.getElementFactory();
    final PsiModifierList privateModifierList;
    try {
      final PsiMethod newMethod = factory.createMethod("x3", PsiType.VOID);
      privateModifierList = newMethod.getModifierList();
      privateModifierList.setModifierProperty(PsiModifier.PRIVATE, true);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
      return false;
    }
    for (PsiReference reference : references) {
      final PsiElement element = reference.getElement();
      if (!isInside(element, allElementsToDelete) && !isInside(element, deleted)
          && !facade.getResolveHelper().isAccessible(method, privateModifierList, element, null, null)) {
        return false;
      }
    }
    return true;
  }

  private static Condition<PsiElement> findFieldUsages(final PsiField psiField, final List<UsageInfo> usages, final PsiElement[] allElementsToDelete) {
    final Condition<PsiElement> isInsideDeleted = getUsageInsideDeletedFilter(allElementsToDelete);
    Set<PsiParameter> parameters = new LinkedHashSet<>();
    ReferencesSearch.search(psiField).forEach(reference -> {
      if (!isInsideDeleted.value(reference.getElement())) {
        final PsiElement element = reference.getElement();
        final PsiElement parent = element.getParent();
        if (parent instanceof PsiAssignmentExpression && element == ((PsiAssignmentExpression)parent).getLExpression()) {
          usages.add(new SafeDeleteFieldWriteReference((PsiAssignmentExpression)parent, psiField));
          PsiExpression rExpression = PsiUtil.skipParenthesizedExprDown(((PsiAssignmentExpression)parent).getRExpression());
          if (rExpression instanceof PsiReferenceExpression) {
            PsiElement resolve = ((PsiReferenceExpression)rExpression).resolve();
            if (resolve instanceof PsiParameter) {
              PsiParameter parameter = (PsiParameter)resolve;
              PsiElement scope = parameter.getDeclarationScope();
              if (scope instanceof PsiMethod && ((PsiMethod)scope).isConstructor()) {
                parameters.add(parameter);
              }
            }
          }
        }
        else {
          TextRange range = reference.getRangeInElement();
          usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(reference.getElement(), psiField, range.getStartOffset(),
                                                                  range.getEndOffset(), false, PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class) != null));
        }
      }

      return true;
    });

    PsiMethod setterPrototype = PropertyUtilBase.generateSetterPrototype(psiField, psiField.getContainingClass());
    PsiParameter setterParameter = setterPrototype.getParameterList().getParameters()[0];
    for (PsiParameter parameter : parameters) {
      PsiElement scope = parameter.getDeclarationScope();
      if (scope instanceof PsiMethod) {
        if (!ReferencesSearch.search(parameter, new LocalSearchScope(scope)).forEach(ref -> {
          PsiElement element = ref.getElement();
          if (element instanceof PsiReferenceExpression) {
            PsiElement parent = PsiUtil.skipParenthesizedExprUp(element.getParent());
            if (parent instanceof PsiAssignmentExpression) {
              PsiExpression lExpression = PsiUtil.skipParenthesizedExprDown(((PsiAssignmentExpression)parent).getLExpression());
              if (lExpression instanceof PsiReferenceExpression && ((PsiReferenceExpression)lExpression).resolve() == psiField) {
                return true;
              }
            }
          }
          return false;
        })) continue;
        usages.add(createParameterCallHierarchyUsageInfo(setterPrototype, setterParameter, (PsiMethod)scope, parameter));
      }
    }

    appendCallees(psiField, usages);
    
    return isInsideDeleted;
  }

  private static SafeDeleteParameterCallHierarchyUsageInfo createParameterCallHierarchyUsageInfo(PsiMethod called,
                                                                                                 PsiParameter calledParameter,
                                                                                                 PsiMethod caller, PsiParameter parameterInCaller) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return new SafeDeleteParameterCallHierarchyUsageInfo(caller, parameterInCaller, caller, parameterInCaller);
    }
    else {
      return new SafeDeleteParameterCallHierarchyUsageInfo(called, calledParameter, caller, parameterInCaller);
    }
  }


  private static void findParameterUsages(final PsiParameter parameter, final List<UsageInfo> usages) {
    final PsiMethod method = (PsiMethod)parameter.getDeclarationScope();
    final int parameterIndex = method.getParameterList().getParameterIndex(parameter);
    //search for refs to current method only, do not search for refs to overriding methods, they'll be searched separately
    ReferencesSearch.search(method).forEach(reference -> {
      PsiElement element = reference.getElement();
      if (element != null) {
        final JavaSafeDeleteDelegate safeDeleteDelegate = JavaSafeDeleteDelegate.EP.forLanguage(element.getLanguage());
        if (safeDeleteDelegate != null) {
          safeDeleteDelegate.createUsageInfoForParameter(reference, usages, parameter, method);
        }
        if (!parameter.isVarArgs() && !RefactoringChangeUtil.isSuperMethodCall(element.getParent())) {
          final PsiParameter paramInCaller = SafeDeleteJavaCallerChooser.isTheOnlyOneParameterUsage(element.getParent(), parameterIndex, method);
          if (paramInCaller != null) {
            final PsiMethod callerMethod = (PsiMethod)paramInCaller.getDeclarationScope();
            usages.add(createParameterCallHierarchyUsageInfo( method, parameter, callerMethod, paramInCaller));
          }
        }
      }
      return true;
    });

    ReferencesSearch.search(parameter).forEach(reference -> {
      PsiElement element = reference.getElement();
      final PsiDocTag docTag = PsiTreeUtil.getParentOfType(element, PsiDocTag.class);
      if (docTag != null) {
        usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(docTag, parameter, true));
        return true;
      }

      boolean isSafeDelete = false;
      if (element.getParent().getParent() instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression call = (PsiMethodCallExpression)element.getParent().getParent();
        PsiReferenceExpression methodExpression = call.getMethodExpression();
        if (methodExpression.getText().equals(PsiKeyword.SUPER)) {
          isSafeDelete = true;
        }
        else if (methodExpression.getQualifierExpression() instanceof PsiSuperExpression) {
          final PsiMethod superMethod = call.resolveMethod();
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


  private static boolean isInside(PsiElement place, PsiElement[] ancestors) {
    return isInside(place, Arrays.asList(ancestors));
  }

  private static boolean isInside(PsiElement place, Collection<? extends PsiElement> ancestors) {
    for (PsiElement element : ancestors) {
      if (isInside(place, element)) return true;
    }
    return false;
  }

  public static boolean isInside (PsiElement place, PsiElement ancestor) {
    if (SafeDeleteProcessor.isInside(place, ancestor)) return true;
    if (PsiTreeUtil.getParentOfType(place, PsiComment.class, false) != null && ancestor instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)ancestor;
      if (aClass.getParent() instanceof PsiJavaFile) {
        final PsiJavaFile file = (PsiJavaFile)aClass.getParent();
        if (PsiTreeUtil.isAncestor(file, place, false)) {
          if (file.getClasses().length == 1) { // file will be deleted on class deletion
            return true;
          }
        }
      }
    }

    return false;
  }

  public static void collectMethodConflicts(MultiMap<PsiElement, String> conflicts, PsiMethod method, PsiParameter parameter) {
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass != null) {
      final int parameterIndex = method.getParameterList().getParameterIndex(parameter);
      final PsiMethod methodCopy = (PsiMethod)method.copy();
      methodCopy.getParameterList().getParameters()[parameterIndex].delete();
      ConflictsUtil.checkMethodConflicts(containingClass, method, methodCopy, conflicts);
    }
  }

  private static class SafeDeleteFunctionalExpressionUsageInfo extends SafeDeleteReferenceUsageInfo {
    public SafeDeleteFunctionalExpressionUsageInfo(@NotNull PsiElement element, PsiElement referencedElement) {
      super(element, referencedElement, false);
    }

    @Override
    public void deleteElement() throws IncorrectOperationException {}
  }

  private static class SafeDeleteExternalAnnotationsUsageInfo extends SafeDeleteReferenceUsageInfo {

    public SafeDeleteExternalAnnotationsUsageInfo(PsiElement referenceElement, PsiElement element) {
      super(element, referenceElement, true);
    }

    @Override
    public void deleteElement() throws IncorrectOperationException {
      PsiElement referencedElement = getReferencedElement();
      if (!referencedElement.isValid()) return;
      ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(referencedElement.getProject());
      PsiAnnotation[] externalAnnotations = annotationsManager.findExternalAnnotations((PsiModifierListOwner)referencedElement);
      if (externalAnnotations != null) {
        for (PsiAnnotation annotation : externalAnnotations) {
          String qualifiedName = annotation.getQualifiedName();
          if (qualifiedName == null) continue;
          annotationsManager.deannotate((PsiModifierListOwner)referencedElement, qualifiedName);
        }
      }
    }
  }
}
