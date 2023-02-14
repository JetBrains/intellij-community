/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExtendsListFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance(ExtendsListFix.class);

  @Nullable
  @SafeFieldForPreview // we don't modify this class
  protected final SmartPsiElementPointer<PsiClass> myClassToExtendFromPointer;
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

    @NonNls final String messageKey;
    if (classToExtendFrom != null && aClass.isInterface() == classToExtendFrom.isInterface()) {
      messageKey = toAdd ? "add.class.to.extends.list" : "remove.class.from.extends.list";
    }
    else {
      messageKey = toAdd ? "add.interface.to.implements.list" : "remove.interface.from.implements.list";
    }

    myName = QuickFixBundle.message(messageKey, aClass.getName(), classToExtendFrom == null ? "" : classToExtendFrom instanceof PsiTypeParameter ? classToExtendFrom.getName()
                                                                                                                                                 : classToExtendFrom.getQualifiedName());
  }

  @Override
  @NotNull
  public String getText() {
    return myName;
  }

  @Override
  @NotNull
  public String getFamilyName() {
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
    UndoUtil.markPsiFileForUndo(file);
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
        modifyList(extendsList, myToAdd, -1);
      }
      if (otherList != null) {
        modifyList(otherList, false, -1);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  /**
   * @param position to add new class to or -1 if add to the end
   */
  void modifyList(@NotNull PsiReferenceList extendsList, boolean add, int position) throws IncorrectOperationException {
    PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
    boolean alreadyExtends = false;
    PsiClass classToExtendFrom = myClassToExtendFromPointer != null ? myClassToExtendFromPointer.getElement() : null;

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
      PsiJavaCodeReferenceElement classReferenceElement = JavaPsiFacade.getElementFactory(project).createReferenceElementByType(myTypeToExtendFrom);
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
