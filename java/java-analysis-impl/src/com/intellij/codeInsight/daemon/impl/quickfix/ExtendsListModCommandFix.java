// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

public class ExtendsListModCommandFix extends PsiUpdateModCommandAction<PsiClass> {
  protected final @Nullable SmartPsiElementPointer<PsiClass> myClassToExtendFromPointer;
  private final boolean myToAdd;
  private final PsiClassType myTypeToExtendFrom;
  private final @IntentionName String myName;

  public ExtendsListModCommandFix(@NotNull PsiClass aClass, @NotNull PsiClassType typeToExtendFrom, boolean toAdd) {
    this(aClass, typeToExtendFrom.resolve(), typeToExtendFrom, toAdd);
  }

  public ExtendsListModCommandFix(@NotNull PsiClass aClass, @NotNull PsiClass classToExtendFrom, boolean toAdd) {
    this(aClass, classToExtendFrom, JavaPsiFacade.getElementFactory(aClass.getProject()).createType(classToExtendFrom), toAdd);
  }

  @ApiStatus.Internal
  public ExtendsListModCommandFix(@NotNull PsiClass aClass,
                                  @Nullable PsiClass classToExtendFrom,
                                  @NotNull PsiClassType typeToExtendFrom,
                                  boolean toAdd) {
    super(aClass);
    myClassToExtendFromPointer = classToExtendFrom == null ? null : SmartPointerManager.createPointer(classToExtendFrom);
    myToAdd = toAdd;
    myTypeToExtendFrom =
      aClass instanceof PsiTypeParameter ? typeToExtendFrom : (PsiClassType)GenericsUtil.eliminateWildcards(typeToExtendFrom);

    @PropertyKey(resourceBundle = QuickFixBundle.BUNDLE) String messageKey;
    if (classToExtendFrom != null && aClass.isInterface() == classToExtendFrom.isInterface() || aClass instanceof PsiTypeParameter) {
      messageKey = toAdd ? "add.class.to.extends.list" : "remove.class.from.extends.list";
    }
    else {
      messageKey = toAdd ? "add.interface.to.implements.list" : "remove.interface.from.implements.list";
    }

    myName = QuickFixBundle.message(messageKey, aClass.getName(), classToExtendFrom == null
                                                                  ? ""
                                                                  : classToExtendFrom instanceof PsiTypeParameter
                                                                    ? classToExtendFrom.getName()
                                                                    : classToExtendFrom.getQualifiedName());
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiClass myClass) {
    if (!myTypeToExtendFrom.isValid()) return null;
    PsiClass classToExtendFrom = myClassToExtendFromPointer != null ? myClassToExtendFromPointer.getElement() : null;
    boolean available = classToExtendFrom != null &&
                        !classToExtendFrom.hasModifierProperty(PsiModifier.FINAL) &&
                        (classToExtendFrom.isInterface() ||
                         !myClass.isInterface() &&
                         myClass.getExtendsList() != null &&
                         (myClass.getExtendsList().getReferencedTypes().length == 0) == myToAdd);
    if (!available) return null;
    return Presentation.of(myName);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("change.extends.list.family");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiClass myClass, @NotNull ModPsiUpdater updater) {
    invokeImpl(myClass);
  }

  @ApiStatus.Internal
  public void invokeImpl(@NotNull PsiClass myClass) {
    PsiClass classToExtendFrom = myClassToExtendFromPointer != null ? myClassToExtendFromPointer.getElement() : null;

    PsiReferenceList extendsList =
      !(myClass instanceof PsiTypeParameter) && classToExtendFrom != null && myClass.isInterface() != classToExtendFrom.isInterface()
      ? myClass.getImplementsList()
      : myClass.getExtendsList();
    PsiReferenceList otherList = extendsList == myClass.getImplementsList() ? myClass.getExtendsList() : myClass.getImplementsList();
    if (extendsList != null) {
      modifyList(extendsList, myToAdd, -1, myTypeToExtendFrom);
    }
    if (otherList != null) {
      modifyList(otherList, false, -1, myTypeToExtendFrom);
    }
  }

  /**
   * @param position to add new class to or -1 if add to the end
   */
  @ApiStatus.Internal
  public static void modifyList(@NotNull PsiReferenceList extendsList, boolean add, int position, @NotNull PsiClassType myTypeToExtendFrom) {
    PsiClass classToExtendFrom = myTypeToExtendFrom.resolve();
    if (classToExtendFrom == null) return;

    PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
    boolean alreadyExtends = false;

    for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
      if (referenceElement.getManager().areElementsEquivalent(classToExtendFrom, referenceElement.resolve())) {
        alreadyExtends = true;
        if (!add) {
          referenceElement.delete();
        }
      }
    }
    PsiReferenceList list = extendsList;
    Project project = extendsList.getProject();
    if (add && !alreadyExtends) {
      PsiElement anchor;
      if (position == -1) {
        anchor = referenceElements.length == 0 ? null : referenceElements[referenceElements.length - 1];
      }
      else if (position == 0) {
        anchor = null;
      }
      else {
        anchor = referenceElements[position - 1];
      }
      PsiJavaCodeReferenceElement classReferenceElement =
        JavaPsiFacade.getElementFactory(project).createReferenceElementByType(myTypeToExtendFrom.annotate(TypeAnnotationProvider.EMPTY));
      PsiElement element;
      if (anchor == null) {
        if (referenceElements.length == 0) {
          element = extendsList.add(classReferenceElement);
        }
        else {
          element = extendsList.addBefore(classReferenceElement, referenceElements[0]);
        }
      }
      else {
        element = extendsList.addAfter(classReferenceElement, anchor);
      }
      list = (PsiReferenceList)element.getParent();
    }

    //nothing was changed
    if (!add && !alreadyExtends) {
      return;
    }

    CodeStyleManager.getInstance(project).reformat(JavaCodeStyleManager.getInstance(project).shortenClassReferences(list));
  }
}
