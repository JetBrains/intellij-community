// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.search.SearchScope;
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

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

public class RenameJavaVariableProcessor extends RenameJavaMemberProcessor {
  private static final Logger LOG = Logger.getInstance(RenameJavaVariableProcessor.class);

  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return element instanceof PsiVariable;
  }

  @Override
  public void renameElement(@NotNull PsiElement psiElement,
                            @NotNull String newName,
                            UsageInfo @NotNull [] usages,
                            @Nullable RefactoringElementListener listener) {
    PsiVariable variable = (PsiVariable) psiElement;
    List<MemberHidesOuterMemberUsageInfo> outerHides = new ArrayList<>();
    List<MemberHidesStaticImportUsageInfo> staticImportHides = new ArrayList<>();

    List<PsiElement> occurrencesToCheckForConflict = new ArrayList<>();
    // rename all references
    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      if (element == null) continue;

      if (usage instanceof MemberHidesStaticImportUsageInfo info) {
        staticImportHides.add(info);
      }
      else if (usage instanceof LocalHidesFieldUsageInfo) {
        PsiJavaCodeReferenceElement collidingRef = (PsiJavaCodeReferenceElement)element;
        PsiElement resolved = collidingRef.resolve();

        if (resolved instanceof PsiField field) {
          qualifyMember(field, collidingRef, newName);
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
        final PsiReference ref = usage instanceof MoveRenameUsageInfo ? usage.getReference() : element.getReference();
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

    if (variable instanceof PsiField field) {
      for (PsiElement occurrence : occurrencesToCheckForConflict) {
        fixPossibleNameCollisionsForFieldRenaming(field, newName, occurrence);
      }
    }

    qualifyOuterMemberReferences(outerHides);
    qualifyStaticImportReferences(staticImportHides);
  }

  private static void fixPossibleNameCollisionsForFieldRenaming(PsiField field, String newName, PsiElement replacedOccurence) throws IncorrectOperationException {
    if (!(replacedOccurence instanceof PsiReferenceExpression ref)) return;
    PsiElement elem = ref.resolve();

    if (elem == null || elem == field || elem.isEquivalentTo(field)) {
      // If reference is unresolved, then field is not hidden by anyone...
      return;
    }

    if (elem instanceof PsiLocalVariable || elem instanceof PsiParameter || (elem instanceof PsiField && elem != replacedOccurence))  {
      qualifyMember(field, replacedOccurence, newName);
    }
  }

  @Override
  public void prepareRenaming(@NotNull PsiElement element,
                              @NotNull String newName,
                              @NotNull Map<PsiElement, String> allRenames,
                              @NotNull SearchScope scope) {
    if (element instanceof PsiRecordComponent component) {
      PsiClass containingClass = component.getContainingClass();
      if (containingClass != null) {
        String name = component.getName();
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
    if (element instanceof PsiParameter param) {
      PsiMethod method = PsiTreeUtil.getParentOfType(element.getParent(), PsiMethod.class);
      if (method != null && JavaPsiRecordUtil.isExplicitCanonicalConstructor(method)) {
        PsiRecordComponent recordComponent = JavaPsiRecordUtil.getComponentForCanonicalConstructorParameter(param);
        allRenames.put(recordComponent, newName);

        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
          addGetter(element, newName, allRenames, containingClass, param.getName());
        }
      }
    }
  }

  private static void addGetter(@NotNull PsiElement element,
                                @NotNull String newName,
                                @NotNull Map<PsiElement, String> allRenames,
                                PsiClass containingClass,
                                String name) {
    PsiMethod explicitGetter = ContainerUtil.find(containingClass.findMethodsByName(name, false), m -> m.getParameterList().isEmpty());

    if (explicitGetter != null) {
      JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(element.getProject());
      AutomaticGetterSetterRenamer.addOverriddenAndImplemented(explicitGetter, newName, null, newName, styleManager, allRenames);
    }
  }

  @Override
  public void findCollisions(@NotNull PsiElement element, @NotNull String newName, @NotNull Map<? extends PsiElement, String> allRenames,
                             @NotNull List<UsageInfo> result) {
    if (element instanceof PsiField field) {
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
                                        @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts,
                                        @NotNull Map<PsiElement, String> allRenames) {
    for (PsiElement psiElement : allRenames.keySet()) {
      forElement(psiElement).findExistingNameConflicts(psiElement, allRenames.get(psiElement), conflicts);
    }
  }

  @Override
  public void findExistingNameConflicts(@NotNull PsiElement element, @NotNull String newName,
                                        @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts) {
    if (element instanceof PsiCompiledElement) return;
    if (element instanceof PsiField refactoredField) {
      if (newName.equals(refactoredField.getName())) return;
      ConflictsUtil.checkFieldConflicts(refactoredField.getContainingClass(), newName, conflicts);
    }
  }

  @Override
  public @Nullable @NonNls String getHelpID(PsiElement element) {
    if (element instanceof PsiField) {
      return HelpID.RENAME_FIELD;
    }
    else if (element instanceof PsiLocalVariable) {
      return HelpID.RENAME_VARIABLE;
    }
    else if (element instanceof PsiParameter) {
      return HelpID.RENAME_PARAMETER;
    }
    return null;
  }

  @Override
  public boolean isToSearchInComments(@NotNull PsiElement element) {
    if (element instanceof PsiField) {
      return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FIELD;
    }
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE;
  }

  @Override
  public void setToSearchInComments(@NotNull PsiElement element, boolean enabled) {
    if (element instanceof PsiField) {
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FIELD = enabled;
    }
    else {
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE = enabled;
    }
  }

  @Override
  public boolean isToSearchForTextOccurrences(@NotNull PsiElement element) {
    if (element instanceof PsiField) {
      return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_FIELD;
    }
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE;
  }

  @Override
  public void setToSearchForTextOccurrences(@NotNull PsiElement element, boolean enabled) {
    if (element instanceof PsiField) {
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_FIELD = enabled;
    }
    else {
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE = enabled;
    }
  }

  private static void findSubmemberHidesFieldCollisions(PsiField field, String newName, List<? super UsageInfo> result) {
    if (field.getContainingClass() == null || field.hasModifierProperty(PsiModifier.PRIVATE)) return;
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

  private static void findLocalHidesFieldCollisions(PsiElement element, String newName, Map<? extends PsiElement, String> allRenames,
                                                    List<? super UsageInfo> result) {
    PsiElement scopeElement;
    if (element instanceof PsiLocalVariable local) {
      scopeElement = CommonJavaRefactoringUtil.getVariableScope(local);
    }
    else if (element instanceof PsiParameter param) {
      scopeElement = param.getDeclarationScope();
    }
    else {
      return;
    }

    PsiClass toplevel = PsiUtil.getTopLevelClass(element);
    if (toplevel == null) return;

    LOG.assertTrue(scopeElement != null);
    scopeElement.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (!expression.isQualified()) {
          PsiElement resolved = expression.resolve();
          if (resolved instanceof PsiField field) {
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
  public String getQualifiedNameAfterRename(@NotNull PsiElement element, @NotNull String newName, boolean nonJava) {
    if (!nonJava || !(element instanceof PsiField field)) return null;
    PsiClass containingClass = field.getContainingClass();
    if (containingClass == null) return null;
    String qualifiedName = containingClass.getQualifiedName();
    return qualifiedName == null ? null : StringUtil.getQualifiedName(qualifiedName, newName);
  }
}
