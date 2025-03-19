// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

public class ExtendsListFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance(ExtendsListFix.class);

  // we don't modify this class
  @SafeFieldForPreview protected final @Nullable SmartPsiElementPointer<PsiClass> myClassToExtendFromPointer;
  private final boolean myToAdd;
  @SafeFieldForPreview // we don't modify PSI referenced from this type
  private final PsiClassType myTypeToExtendFrom;
  private final @IntentionName String myName;

  public ExtendsListFix(@NotNull PsiClass aClass, @NotNull PsiClassType typeToExtendFrom, boolean toAdd) {
    this(aClass, typeToExtendFrom.resolve(), typeToExtendFrom, toAdd);
  }

  public ExtendsListFix(@NotNull PsiClass aClass, @NotNull PsiClass classToExtendFrom, boolean toAdd) {
    this(aClass, classToExtendFrom, JavaPsiFacade.getElementFactory(aClass.getProject()).createType(classToExtendFrom), toAdd);
  }

  private ExtendsListFix(@NotNull PsiClass aClass,
                         @Nullable PsiClass classToExtendFrom,
                         @NotNull PsiClassType typeToExtendFrom,
                         boolean toAdd) {
    super(aClass);
    myClassToExtendFromPointer = classToExtendFrom == null ? null : SmartPointerManager.createPointer(classToExtendFrom);
    myToAdd = toAdd;
    myTypeToExtendFrom = aClass instanceof PsiTypeParameter ? typeToExtendFrom : (PsiClassType)GenericsUtil.eliminateWildcards(typeToExtendFrom);

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
  public @NotNull String getText() {
    return myName;
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("change.extends.list.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    if (!myTypeToExtendFrom.isValid()) return false;
    final PsiClass myClass = (PsiClass)startElement;
    PsiClass classToExtendFrom = myClassToExtendFromPointer != null ? myClassToExtendFromPointer.getElement() : null;
    return
      BaseIntentionAction.canModify(myClass)
      && startElement.isPhysical()
      && classToExtendFrom != null
      && classToExtendFrom.isValid()
      && !classToExtendFrom.hasModifierProperty(PsiModifier.FINAL)
      && (classToExtendFrom.isInterface()
          || !myClass.isInterface()
              && myClass.getExtendsList() != null
              && (myClass.getExtendsList().getReferencedTypes().length == 0) == myToAdd);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiClass myClass = (PsiClass)startElement;
    invokeImpl(myClass);
  }

  protected void invokeImpl(PsiClass myClass) {
    PsiClass classToExtendFrom = myClassToExtendFromPointer != null ? myClassToExtendFromPointer.getElement() : null;

    PsiReferenceList extendsList = !(myClass instanceof PsiTypeParameter) && classToExtendFrom != null &&
                                   myClass.isInterface() != classToExtendFrom.isInterface() ?
                                   myClass.getImplementsList() : myClass.getExtendsList();
    PsiReferenceList otherList = extendsList == myClass.getImplementsList() ?
                                 myClass.getExtendsList() : myClass.getImplementsList();
    try {
      if (extendsList != null) {
        modifyList(extendsList, myToAdd, -1, myTypeToExtendFrom);
      }
      if (otherList != null) {
        modifyList(otherList, false, -1, myTypeToExtendFrom);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  /**
   * @param position to add new class to or -1 if add to the end
   */
  static void modifyList(@NotNull PsiReferenceList extendsList, boolean add, int position, @NotNull PsiClassType myTypeToExtendFrom) {
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
        anchor = referenceElements.length ==0 ? null : referenceElements[referenceElements.length-1];
      }
      else if (position == 0) {
        anchor = null;
      }
      else {
        anchor = referenceElements[position - 1];
      }
      PsiJavaCodeReferenceElement classReferenceElement = JavaPsiFacade.getElementFactory(project)
        .createReferenceElementByType(myTypeToExtendFrom.annotate(TypeAnnotationProvider.EMPTY));
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
      list = (PsiReferenceList) element.getParent();
    }

    //nothing was changed
    if (!add && !alreadyExtends) {
      return;
    }

    CodeStyleManager.getInstance(project).reformat(JavaCodeStyleManager.getInstance(project).shortenClassReferences(list));
  }
}
