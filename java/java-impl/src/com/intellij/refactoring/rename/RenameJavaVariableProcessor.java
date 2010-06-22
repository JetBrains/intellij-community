/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PropertyUtil;
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
import com.intellij.util.Processor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class RenameJavaVariableProcessor extends RenameJavaMemberProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenameJavaVariableProcessor");

  public boolean canProcessElement(final PsiElement element) {
    return element instanceof PsiVariable;
  }

  public void renameElement(final PsiElement psiElement,
                            final String newName,
                            final UsageInfo[] usages, final RefactoringElementListener listener) throws IncorrectOperationException {
    PsiVariable variable = (PsiVariable) psiElement;
    List<MemberHidesOuterMemberUsageInfo> outerHides = new ArrayList<MemberHidesOuterMemberUsageInfo>();
    List<MemberHidesStaticImportUsageInfo> staticImportHides = new ArrayList<MemberHidesStaticImportUsageInfo>();

    List<PsiElement> occurrencesToCheckForConflict = new ArrayList<PsiElement>();
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
    listener.elementRenamed(variable);

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

    if (elem == null || elem == field) {
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

    final String propertyName = manager.variableNameToPropertyName(field.getName(), VariableKind.FIELD);
    String newPropertyName = manager.variableNameToPropertyName(newName, VariableKind.FIELD);

    boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    PsiMethod getter = PropertyUtil.findPropertyGetter(aClass, propertyName, isStatic, false);
    PsiMethod setter = PropertyUtil.findPropertySetter(aClass, propertyName, isStatic, false);

    boolean shouldRenameSetterParameter = false;

    if (setter != null) {
      String parameterName = manager.propertyNameToVariableName(propertyName, VariableKind.PARAMETER);
      PsiParameter setterParameter = setter.getParameterList().getParameters()[0];
      shouldRenameSetterParameter = parameterName.equals(setterParameter.getName());
    }

    String newGetterName = "";

    if (getter != null) {
      String getterId = getter.getName();
      newGetterName = PropertyUtil.suggestGetterName(newPropertyName, field.getType(), getterId);
      if (newGetterName.equals(getterId)) {
        getter = null;
        newGetterName = null;
      }
    }

    String newSetterName = "";
    if (setter != null) {
      newSetterName = PropertyUtil.suggestSetterName(newPropertyName);
      final String newSetterParameterName = manager.propertyNameToVariableName(newPropertyName, VariableKind.PARAMETER);
      if (newSetterName.equals(setter.getName())) {
        setter = null;
        newSetterName = null;
        shouldRenameSetterParameter = false;
      }
      else if (newSetterParameterName.equals(setter.getParameterList().getParameters()[0].getName())) {
        shouldRenameSetterParameter = false;
      }
    }

    if ((getter != null || setter != null) && askToRenameAccesors(getter, setter, newName, project)) {
      getter = null;
      setter = null;
      shouldRenameSetterParameter = false;
    }

    if (getter != null) {
      addOverriddenAndImplemented(getter, newGetterName, allRenames);
    }

    if (setter != null) {
      addOverriddenAndImplemented(setter, newSetterName, allRenames);
    }

    if (shouldRenameSetterParameter) {
      PsiParameter parameter = setter.getParameterList().getParameters()[0];
      allRenames.put(parameter, manager.propertyNameToVariableName(newPropertyName, VariableKind.PARAMETER));
    }
  }

  private static boolean askToRenameAccesors(PsiMethod getter, PsiMethod setter, String newName, final Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return false;
    String text = RefactoringMessageUtil.getGetterSetterMessage(newName, RefactoringBundle.message("rename.title"), getter, setter);
    return Messages.showYesNoDialog(project, text, RefactoringBundle.message("rename.title"), Messages.getQuestionIcon()) != 0;
  }

  private static void addOverriddenAndImplemented(PsiMethod methodPrototype, final String newName, final Map<PsiElement, String> allRenames) {
    allRenames.put(methodPrototype, newName);
    for (PsiMethod method : methodPrototype.findDeepestSuperMethods()) {
      OverridingMethodsSearch.search(method).forEach(new Processor<PsiMethod>() {
        public boolean process(PsiMethod psiMethod) {
          allRenames.put(psiMethod, newName);
          return true;
        }
      });
      allRenames.put(method, newName);
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
    Collection<PsiClass> inheritors = ClassInheritorsSearch.search(containingClass, containingClass.getUseScope(), true).findAll();
    for (PsiClass inheritor : inheritors) {
      PsiField conflictingField = inheritor.findFieldByName(newName, false);
      if (conflictingField != null) {
        result.add(new SubmemberHidesMemberUsageInfo(conflictingField, field));
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
