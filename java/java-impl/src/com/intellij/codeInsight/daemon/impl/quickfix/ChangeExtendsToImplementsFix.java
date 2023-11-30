/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * changes 'class a extends b' to 'class a implements b' or vice versa
 */
public class ChangeExtendsToImplementsFix extends PsiUpdateModCommandAction<PsiClass> {
  @Nullable
  protected final SmartPsiElementPointer<PsiClass> myClassToExtendFromPointer;
  private final boolean myToAdd;
  private final PsiClassType myTypeToExtendFrom;

  public ChangeExtendsToImplementsFix(@NotNull PsiClass aClass, @NotNull PsiClassType classTypeToExtendFrom) {
    super(aClass);
    PsiClass classToExtendFrom = classTypeToExtendFrom.resolve();
    myClassToExtendFromPointer = classToExtendFrom == null ? null : SmartPointerManager.createPointer(classToExtendFrom);
    myToAdd = true;
    myTypeToExtendFrom = aClass instanceof PsiTypeParameter ? classTypeToExtendFrom
                                                            : (PsiClassType)GenericsUtil.eliminateWildcards(classTypeToExtendFrom);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("change.extends.list.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiClass myClass) {
    if (!myTypeToExtendFrom.isValid()) return null;
    PsiClass classToExtendFrom = myClassToExtendFromPointer != null ? myClassToExtendFromPointer.getElement() : null;
    boolean available = classToExtendFrom != null && classToExtendFrom.isValid()
                        && !classToExtendFrom.hasModifierProperty(PsiModifier.FINAL)
                        && (classToExtendFrom.isInterface() ||
                            !myClass.isInterface() && myClass.getExtendsList() != null
                            && (myClass.getExtendsList().getReferencedTypes().length == 0) == myToAdd);
    if (!available) return null;
    String name = QuickFixBundle.message(
      "exchange.extends.implements.keyword",
      myClass.isInterface() == classToExtendFrom.isInterface() ? PsiKeyword.IMPLEMENTS : PsiKeyword.EXTENDS,
      myClass.isInterface() == classToExtendFrom.isInterface() ? PsiKeyword.EXTENDS : PsiKeyword.IMPLEMENTS,
      classToExtendFrom.getName());
    return Presentation.of(name).withPriority(PriorityAction.Priority.HIGH);

  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiClass myClass, @NotNull ModPsiUpdater updater) {
    PsiClass classToExtendFrom = myClassToExtendFromPointer != null ? myClassToExtendFromPointer.getElement() : null;

    PsiReferenceList extendsList = !(myClass instanceof PsiTypeParameter) && classToExtendFrom != null &&
                                   myClass.isInterface() != classToExtendFrom.isInterface() ?
                                   myClass.getImplementsList() : myClass.getExtendsList();
    PsiReferenceList otherList = extendsList == myClass.getImplementsList() ?
                                 myClass.getExtendsList() : myClass.getImplementsList();
    if (extendsList != null) {
      ExtendsListFix.modifyList(extendsList, myToAdd, -1, myTypeToExtendFrom);
    }
    if (otherList != null) {
      ExtendsListFix.modifyList(otherList, false, -1, myTypeToExtendFrom);
    }
  }
}