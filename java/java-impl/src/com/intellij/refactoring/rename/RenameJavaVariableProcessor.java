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
package com.intellij.refactoring.rename;

import com.intellij.codeInsight.generation.GetterSetterPrototypeProvider;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class RenameJavaVariableProcessor extends RenameJavaMemberProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenameJavaVariableProcessor");

  public boolean canProcessElement(@NotNull final PsiElement element) {
    return element instanceof PsiVariable;
  }

  public void renameElement(final PsiElement psiElement,
                            final String newName,
                            final UsageInfo[] usages,
                            @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
    PsiVariable variable = (PsiVariable) psiElement;
    List<MemberHidesOuterMemberUsageInfo> outerHides = new ArrayList<>();
    List<MemberHidesStaticImportUsageInfo> staticImportHides = new ArrayList<>();

    List<PsiElement> occurrencesToCheckForConflict = new ArrayList<>();
    // rename all references
    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      if (element == null) continue;

      if (usage instanceof MemberHidesStaticImportUsageInfo) {
        staticImportHides.add((MemberHidesStaticImportUsageInfo)usage);
      } else if (usage instanceof LocalHidesFieldUsageInfo) {
        PsiJavaCodeReferenceElement collidingRef = (PsiJavaCodeReferenceElement)element;
        PsiElement resolved = collidingRef.resolve();

        if (resolved instanceof PsiField) {
          qualifyMember((PsiField)resolved, collidingRef, newName);
        }
        else {
          // do nothing
        }
      }
      else if (usage instanceof MemberHidesOuterMemberUsageInfo) {
        PsiJavaCodeReferenceElement collidingRef = (PsiJavaCodeReferenceElement)element;
        PsiField resolved = (PsiField)collidingRef.resolve();
        outerHides.add(new MemberHidesOuterMemberUsageInfo(element, resolved));
      }
      else {
        final PsiReference ref;
        if (usage instanceof MoveRenameUsageInfo) {
          ref = usage.getReference();
        }
        else {
          ref = element.getReference();
        }
        if (ref != null) {
          PsiElement newElem = ref.handleElementRename(newName);
          if (variable instanceof PsiField) {
            occurrencesToCheckForConflict.add(newElem);
          }
        }
      }
      }
    // do actual rename
    variable.setName(newName);
    if (listener != null) {
      listener.elementRenamed(variable);
    }

    if (variable instanceof PsiField) {
      for (PsiElement occurrence : occurrencesToCheckForConflict) {
        fixPossibleNameCollisionsForFieldRenaming((PsiField) variable, newName, occurrence);
      }
    }

    qualifyOuterMemberReferences(outerHides);
    qualifyStaticImportReferences(staticImportHides);
  }

  private static void fixPossibleNameCollisionsForFieldRenaming(PsiField field, String newName, PsiElement replacedOccurence) throws IncorrectOperationException {
    if (!(replacedOccurence instanceof PsiReferenceExpression)) return;
    PsiElement elem = ((PsiReferenceExpression)replacedOccurence).resolve();

    if (elem == null || elem == field || elem.isEquivalentTo(field)) {
      // If reference is unresolved, then field is not hidden by anyone...
      return;
    }

    if (elem instanceof PsiLocalVariable || elem instanceof PsiParameter || (elem instanceof PsiField && elem != replacedOccurence))  {
      qualifyMember(field, replacedOccurence, newName);
    }
  }

  public void prepareRenaming(final PsiElement element, final String newName, final Map<PsiElement, String> allRenames) {
    if (element instanceof PsiField && StdLanguages.JAVA.equals(element.getLanguage())) {
      prepareFieldRenaming((PsiField)element, newName, allRenames);
    }
  }

  private static void prepareFieldRenaming(PsiField field, String newName, final Map<PsiElement, String> allRenames) {
    // search for getters/setters
    PsiClass aClass = field.getContainingClass();

    Project project = field.getProject();
    final JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(project);

    final String propertyName = PropertyUtilBase.suggestPropertyName(field, field.getName());
    final String newPropertyName = PropertyUtilBase.suggestPropertyName(field, newName);

    boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);

    PsiMethod[] getters = GetterSetterPrototypeProvider.findGetters(aClass, propertyName, isStatic);

    PsiMethod setter = PropertyUtilBase.findPropertySetter(aClass, propertyName, isStatic, false);

    boolean shouldRenameSetterParameter = false;

    if (setter != null) {
      shouldRenameSetterParameter = shouldRenameSetterParameter(manager, propertyName, setter);
    }

    if (getters != null) {
      List<PsiMethod> validGetters = new ArrayList<>();
      for (PsiMethod getter : getters) {
        String newGetterName = GetterSetterPrototypeProvider.suggestNewGetterName(propertyName, newPropertyName, getter);
        String getterId = null;
        if (newGetterName == null) {
          getterId = getter.getName();
          newGetterName = PropertyUtilBase.suggestGetterName(newPropertyName, field.getType(), getterId);
        }
        if (newGetterName.equals(getterId)) {
          continue;
        }
        else {
          boolean valid = true;
          for (PsiMethod method : getter.findDeepestSuperMethods()) {
            if (method instanceof PsiCompiledElement) {
              valid = false;
              break;
            }
          }
          if (!valid) continue;
        }
        validGetters.add(getter);
      }
      getters = validGetters.isEmpty() ? null : validGetters.toArray(new PsiMethod[validGetters.size()]);
    }

    String newSetterName = "";
    if (setter != null) {
      newSetterName = PropertyUtilBase.suggestSetterName(newPropertyName);
      final String newSetterParameterName = manager.propertyNameToVariableName(newPropertyName, VariableKind.PARAMETER);
      if (newSetterName.equals(setter.getName())) {
        setter = null;
        newSetterName = null;
        shouldRenameSetterParameter = false;
      }
      else if (newSetterParameterName.equals(setter.getParameterList().getParameters()[0].getName())) {
        shouldRenameSetterParameter = false;
      } else {
        for (PsiMethod method : setter.findDeepestSuperMethods()) {
          if (method instanceof PsiCompiledElement) {
            setter = null;
            shouldRenameSetterParameter = false;
            break;
          }
        }
      }
    }

    if ((getters != null || setter != null) && askToRenameAccesors(getters != null ? getters[0] : null, setter, newName, project)) {
      getters = null;
      setter = null;
      shouldRenameSetterParameter = false;
    }

    if (getters != null) {
      for (PsiMethod getter : getters) {
        String newGetterName = GetterSetterPrototypeProvider.suggestNewGetterName(propertyName, newPropertyName, getter);
        if (newGetterName == null) {
          newGetterName = PropertyUtilBase.suggestGetterName(newPropertyName, field.getType(), getter.getName());
        }
        addOverriddenAndImplemented(getter, newGetterName, null, propertyName, manager, allRenames);
      }
    }

    if (setter != null) {
      addOverriddenAndImplemented(setter, newSetterName, shouldRenameSetterParameter ? newPropertyName : null, propertyName, manager, allRenames);
    }
  }

  private static boolean shouldRenameSetterParameter(JavaCodeStyleManager manager, String propertyName, PsiMethod setter) {
    boolean shouldRenameSetterParameter;
    String parameterName = manager.propertyNameToVariableName(propertyName, VariableKind.PARAMETER);
    PsiParameter setterParameter = setter.getParameterList().getParameters()[0];
    shouldRenameSetterParameter = parameterName.equals(setterParameter.getName());
    return shouldRenameSetterParameter;
  }

  private static boolean askToRenameAccesors(PsiMethod getter, PsiMethod setter, String newName, final Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return false;
    boolean physicalGetter = getter != null && getter.isPhysical();
    boolean physicalSetter = setter != null && setter.isPhysical();
    if (!physicalGetter && !physicalSetter) return false;
    String text = RefactoringMessageUtil.getGetterSetterMessage(newName, RefactoringBundle.message("rename.title"), getter, setter);
    return Messages.showYesNoDialog(project, text, RefactoringBundle.message("rename.title"), Messages.getQuestionIcon()) != Messages.YES;
  }

  private static void addOverriddenAndImplemented(@NotNull final PsiMethod methodPrototype,
                                                  @NotNull final String newName,
                                                  @Nullable final String newPropertyName,
                                                  @NotNull final String oldParameterName,
                                                  @NotNull final JavaCodeStyleManager manager,
                                                  @NotNull final Map<PsiElement, String> allRenames) {
    PsiMethod[] methods = methodPrototype.findDeepestSuperMethods();
    if (methods.length == 0) {
      methods = new PsiMethod[] {methodPrototype};
    }
    for (PsiMethod method : methods) {
      addGetterOrSetterWithParameter(method, newName, newPropertyName, oldParameterName, manager, allRenames);
      OverridingMethodsSearch.search(method).forEach(psiMethod -> {
        RenameProcessor.assertNonCompileElement(psiMethod);
        addGetterOrSetterWithParameter(psiMethod, newName, newPropertyName, oldParameterName, manager, allRenames);
        return true;
      });
    }
  }

  public static void addGetterOrSetterWithParameter(@NotNull PsiMethod methodPrototype,
                                                    @NotNull String newName,
                                                    @Nullable String newPropertyName,
                                                    @NotNull String oldParameterName,
                                                    @NotNull JavaCodeStyleManager manager,
                                                    @NotNull Map<PsiElement, String> allRenames) {
    
    allRenames.put(methodPrototype, newName);
    if (newPropertyName != null) {
      final PsiParameter[] parameters = methodPrototype.getParameterList().getParameters();
      LOG.assertTrue(parameters.length > 0, methodPrototype.getName());
      PsiParameter parameter = parameters[0];
      if (shouldRenameSetterParameter(manager, oldParameterName , methodPrototype)) {
        allRenames.put(parameter, manager.propertyNameToVariableName(newPropertyName, VariableKind.PARAMETER));
      }
    }
  }

  public void findCollisions(final PsiElement element, final String newName, final Map<? extends PsiElement, String> allRenames,
                             final List<UsageInfo> result) {
    if (element instanceof PsiField) {
      PsiField field = (PsiField) element;
      findMemberHidesOuterMemberCollisions(field, newName, result);
      findSubmemberHidesFieldCollisions(field, newName, result);
      findCollisionsAgainstNewName(field, newName, result);
    }
    else if (element instanceof PsiLocalVariable || element instanceof PsiParameter) {
      JavaUnresolvableLocalCollisionDetector.findCollisions(element, newName, result);
      findLocalHidesFieldCollisions(element, newName, allRenames, result);
    }
  }

  @Override
  public void findExistingNameConflicts(PsiElement element,
                                        String newName,
                                        MultiMap<PsiElement, String> conflicts,
                                        Map<PsiElement, String> allRenames) {
    for (PsiElement psiElement : allRenames.keySet()) {
      RenamePsiElementProcessor.forElement(psiElement).findExistingNameConflicts(psiElement, allRenames.get(psiElement), conflicts);
    }
  }

  @Override
  public void findExistingNameConflicts(PsiElement element, String newName, MultiMap<PsiElement, String> conflicts) {
    if (element instanceof PsiCompiledElement) return;
    if (element instanceof PsiField) {
      PsiField refactoredField = (PsiField)element;
      if (newName.equals(refactoredField.getName())) return;
      ConflictsUtil.checkFieldConflicts(
        refactoredField.getContainingClass(),
        newName,
        conflicts
      );
    }
  }

  @Nullable
  @NonNls
  public String getHelpID(final PsiElement element) {
    if (element instanceof PsiField){
      return HelpID.RENAME_FIELD;
    }
    else if (element instanceof PsiLocalVariable){
      return HelpID.RENAME_VARIABLE;
    }
    else if (element instanceof PsiParameter){
      return HelpID.RENAME_PARAMETER;
    }
    return null;
  }

  public boolean isToSearchInComments(final PsiElement element) {
    if (element instanceof PsiField){
      return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FIELD;
    }
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE;
  }

  public void setToSearchInComments(final PsiElement element, final boolean enabled) {
    if (element instanceof PsiField){
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FIELD = enabled;
    }
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE = enabled;
  }

  public boolean isToSearchForTextOccurrences(final PsiElement element) {
    if (element instanceof PsiField) {
      return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FIELD;
    }
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE;
  }

  public void setToSearchForTextOccurrences(final PsiElement element, final boolean enabled) {
    if (element instanceof PsiField) {
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FIELD = enabled;
    }
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE = enabled;
  }

  private static void findSubmemberHidesFieldCollisions(final PsiField field, final String newName, final List<UsageInfo> result) {
    if (field.getContainingClass() == null) return;
    if (field.hasModifierProperty(PsiModifier.PRIVATE)) return;
    final PsiClass containingClass = field.getContainingClass();
    Collection<PsiClass> inheritors = ClassInheritorsSearch.search(containingClass).findAll();
    for (PsiClass inheritor : inheritors) {
      PsiField conflictingField = inheritor.findFieldByName(newName, false);
      if (conflictingField != null) {
        result.add(new SubmemberHidesMemberUsageInfo(conflictingField, field));
      } 
      else { //local class
        final PsiMember member = PsiTreeUtil.getParentOfType(inheritor, PsiMember.class);
        if (member != null) {
          final ArrayList<PsiVariable> variables = new ArrayList<>();
          ControlFlowUtil.collectOuterLocals(variables, inheritor, inheritor, member);
          for (PsiVariable variable : variables) {
            if (newName.equals(variable.getName())) {
              result.add(new FieldHidesLocalUsageInfo(variable, field));
            }
          }
        }
      }
    }
  }

  private static void findLocalHidesFieldCollisions(final PsiElement element, final String newName, final Map<? extends PsiElement, String> allRenames, final List<UsageInfo> result) {
    if (!(element instanceof PsiLocalVariable) && !(element instanceof PsiParameter)) return;

    PsiClass toplevel = PsiUtil.getTopLevelClass(element);
    if (toplevel == null) return;

    PsiElement scopeElement;
    if (element instanceof PsiLocalVariable) {
      scopeElement = RefactoringUtil.getVariableScope((PsiLocalVariable)element);
    }
    else { // Parameter
      scopeElement = ((PsiParameter) element).getDeclarationScope();
    }

    LOG.assertTrue(scopeElement != null);
    scopeElement.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (!expression.isQualified()) {
          PsiElement resolved = expression.resolve();
          if (resolved instanceof PsiField) {
            final PsiField field = (PsiField)resolved;
            String fieldNewName = allRenames.containsKey(field) ? allRenames.get(field) : field.getName();
            if (newName.equals(fieldNewName)) {
              result.add(new LocalHidesFieldUsageInfo(expression, element));
            }
          }
        }
      }
    });
  }
}
