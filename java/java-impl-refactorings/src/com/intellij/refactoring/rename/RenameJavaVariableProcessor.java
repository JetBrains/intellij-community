// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.naming.AutomaticGetterSetterRenamer;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class RenameJavaVariableProcessor extends RenameJavaMemberProcessor {
  private static final Logger LOG = Logger.getInstance(RenameJavaVariableProcessor.class);

  @Override
  public boolean canProcessElement(@NotNull final PsiElement element) {
    return element instanceof PsiVariable;
  }

  @Override
  public void renameElement(@NotNull final PsiElement psiElement,
                            @NotNull final String newName,
                            final UsageInfo @NotNull [] usages,
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

  @Override
  public void prepareRenaming(@NotNull final PsiElement element, @NotNull final String newName, @NotNull final Map<PsiElement, String> allRenames) {
    if (element instanceof PsiRecordComponent) {
      PsiClass containingClass = ((PsiRecordComponent)element).getContainingClass();
      if (containingClass != null) {
        String name = ((PsiRecordComponent)element).getName();
        if (name != null) {
          addGetter(element, newName, allRenames, containingClass, name);

          PsiMethod canonicalConstructor = ContainerUtil.find(containingClass.getConstructors(), c -> JavaPsiRecordUtil.isExplicitCanonicalConstructor(c));
          if (canonicalConstructor != null) {
            PsiParameter parameter = ContainerUtil.find(canonicalConstructor.getParameterList().getParameters(), p -> name.equals(p.getName()));
            if (parameter != null) {
              allRenames.put(parameter, newName);
            }
          }
        }
      }
    }
    if (element instanceof PsiParameter) {
      PsiMethod method = PsiTreeUtil.getParentOfType(element.getParent(), PsiMethod.class);
      if (method == null) return;
      if (JavaPsiRecordUtil.isExplicitCanonicalConstructor(method)) {
        PsiRecordComponent recordComponent = JavaPsiRecordUtil.getComponentForCanonicalConstructorParameter((PsiParameter)element);
        allRenames.put(recordComponent, newName);

        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
          addGetter(element, newName, allRenames, containingClass, ((PsiParameter)element).getName());
        }
      }
    }
  }

  private static void addGetter(@NotNull PsiElement element,
                                @NotNull String newName,
                                @NotNull Map<PsiElement, String> allRenames,
                                PsiClass containingClass,
                                String name) {
    PsiMethod explicitGetter = ContainerUtil
      .find(containingClass.findMethodsByName(name, false), m -> m.getParameterList().isEmpty());

    if (explicitGetter != null) {
      AutomaticGetterSetterRenamer
        .addOverriddenAndImplemented(explicitGetter, newName, null, newName, JavaCodeStyleManager.getInstance(element.getProject()),
                                     allRenames);
    }
  }

  @Override
  public void findCollisions(@NotNull final PsiElement element, @NotNull final String newName, @NotNull final Map<? extends PsiElement, String> allRenames,
                             @NotNull final List<UsageInfo> result) {
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
  public void findExistingNameConflicts(@NotNull PsiElement element,
                                        @NotNull String newName,
                                        @NotNull MultiMap<PsiElement, String> conflicts,
                                        @NotNull Map<PsiElement, String> allRenames) {
    for (PsiElement psiElement : allRenames.keySet()) {
      RenamePsiElementProcessor.forElement(psiElement).findExistingNameConflicts(psiElement, allRenames.get(psiElement), conflicts);
    }
  }

  @Override
  public void findExistingNameConflicts(@NotNull PsiElement element, @NotNull String newName, @NotNull MultiMap<PsiElement, String> conflicts) {
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

  @Override
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

  @Override
  public boolean isToSearchInComments(@NotNull final PsiElement element) {
    if (element instanceof PsiField){
      return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FIELD;
    }
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE;
  }

  @Override
  public void setToSearchInComments(@NotNull final PsiElement element, final boolean enabled) {
    if (element instanceof PsiField){
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FIELD = enabled;
    }
    else {
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE = enabled;
    }
  }

  @Override
  public boolean isToSearchForTextOccurrences(@NotNull final PsiElement element) {
    if (element instanceof PsiField) {
      return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_FIELD;
    }
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE;
  }

  @Override
  public void setToSearchForTextOccurrences(@NotNull final PsiElement element, final boolean enabled) {
    if (element instanceof PsiField) {
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_FIELD = enabled;
    }
    else {
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE = enabled;
    }
  }

  private static void findSubmemberHidesFieldCollisions(final PsiField field, final String newName, final List<? super UsageInfo> result) {
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

  private static void findLocalHidesFieldCollisions(final PsiElement element, final String newName, final Map<? extends PsiElement, String> allRenames, final List<? super UsageInfo> result) {
    if (!(element instanceof PsiLocalVariable) && !(element instanceof PsiParameter)) return;

    PsiClass toplevel = PsiUtil.getTopLevelClass(element);
    if (toplevel == null) return;

    PsiElement scopeElement;
    if (element instanceof PsiLocalVariable) {
      scopeElement = CommonJavaRefactoringUtil.getVariableScope((PsiLocalVariable)element);
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
  
  @Override
  public String getQualifiedNameAfterRename(@NotNull final PsiElement element, @NotNull final String newName, final boolean nonJava) {
    if (nonJava && element instanceof PsiField) {
      final PsiField field = (PsiField)element;
      PsiClass containingClass = field.getContainingClass();
      if (containingClass != null) {
        String qualifiedName = containingClass.getQualifiedName();
        if (qualifiedName != null) {
          return StringUtil.getQualifiedName(qualifiedName, newName);
        }
      }
    }
    
    return null;
  }
}
