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
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExtendsListFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.ExtendsListFix");

  final PsiClass myClassToExtendFrom;
  private final boolean myToAdd;
  private final PsiClassType myTypeToExtendFrom;
  private final String myName;

  public ExtendsListFix(@NotNull PsiClass aClass, @NotNull PsiClassType typeToExtendFrom, boolean toAdd) {
    this(aClass, typeToExtendFrom.resolve(), typeToExtendFrom, toAdd);
  }

  public ExtendsListFix(@NotNull PsiClass aClass, @NotNull PsiClass classToExtendFrom, boolean toAdd) {
    this(aClass, classToExtendFrom, JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(classToExtendFrom), toAdd);
  }

  private ExtendsListFix(@NotNull PsiClass aClass,
                         PsiClass classToExtendFrom,
                         @NotNull PsiClassType typeToExtendFrom,
                         boolean toAdd) {
    super(aClass);
    myClassToExtendFrom = classToExtendFrom;
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
    final PsiClass myClass = (PsiClass)startElement;
    return
      myClass.getManager().isInProject(myClass)
      && myClassToExtendFrom != null
      && myClassToExtendFrom.isValid()
      && !myClassToExtendFrom.hasModifierProperty(PsiModifier.FINAL)
      && (myClassToExtendFrom.isInterface()
          || !myClass.isInterface()
              && myClass.getExtendsList() != null
              && myClass.getExtendsList().getReferencedTypes().length == 0 == myToAdd)
        ;

  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable("is null when called from inspection") Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiClass myClass = (PsiClass)startElement;
    invokeImpl(myClass);
    UndoUtil.markPsiFileForUndo(file);
  }

  protected void invokeImpl(PsiClass myClass) {
    PsiReferenceList extendsList = !(myClass instanceof PsiTypeParameter) &&
                                   myClass.isInterface() != myClassToExtendFrom.isInterface() ?
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
  PsiReferenceList modifyList(@NotNull PsiReferenceList extendsList, boolean add, int position) throws IncorrectOperationException {
    PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
    boolean alreadyExtends = false;
    for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
      if (referenceElement.getManager().areElementsEquivalent(myClassToExtendFrom, referenceElement.resolve())) {
        alreadyExtends = true;
        if (!add) {
          referenceElement.delete();
        }
      }
    }
    PsiReferenceList list = extendsList;
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
      PsiJavaCodeReferenceElement classReferenceElement =
        JavaPsiFacade.getInstance(extendsList.getProject()).getElementFactory().createReferenceElementByType(myTypeToExtendFrom);
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
      return list;
    }
    
    return (PsiReferenceList)JavaCodeStyleManager.getInstance(extendsList.getProject()).shortenClassReferences(list);
  }
}
