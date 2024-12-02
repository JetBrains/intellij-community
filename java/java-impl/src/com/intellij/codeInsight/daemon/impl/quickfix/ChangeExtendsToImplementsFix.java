// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * changes 'class a extends b' to 'class a implements b' or vice versa
 */
public class ChangeExtendsToImplementsFix extends PsiUpdateModCommandAction<PsiClass> {
  protected final @Nullable SmartPsiElementPointer<PsiClass> myClassToExtendFromPointer;
  private final boolean myToAdd;
  private final String myTypeToExtendFrom;

  public ChangeExtendsToImplementsFix(@NotNull PsiClass aClass, @NotNull PsiClassType classTypeToExtendFrom) {
    super(aClass);
    PsiClass classToExtendFrom = classTypeToExtendFrom.resolve();
    myClassToExtendFromPointer = classToExtendFrom == null ? null : SmartPointerManager.createPointer(classToExtendFrom);
    myToAdd = true;
    PsiClassType classType = aClass instanceof PsiTypeParameter ? classTypeToExtendFrom
                                                                : (PsiClassType)GenericsUtil.eliminateWildcards(classTypeToExtendFrom);
    PsiType typeToExtend = PsiTypesUtil.removeExternalAnnotations(classType);
    if (typeToExtend.getAnnotations().length > 0) {
      typeToExtend = typeToExtend.annotate(TypeAnnotationProvider.Static.create(PsiAnnotation.EMPTY_ARRAY));
    }
    myTypeToExtendFrom = typeToExtend.getCanonicalText(true);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("change.extends.list.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiClass myClass) {
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
    PsiTypeElement typeElementFromText =
      PsiElementFactory.getInstance(context.project()).createTypeElementFromText(myTypeToExtendFrom, myClass);
    PsiType psiType = typeElementFromText.getType();
    if(!(psiType instanceof PsiClassType psiClassType)) return;
    if (extendsList != null) {
      ExtendsListFix.modifyList(extendsList, myToAdd, -1, psiClassType);
    }
    if (otherList != null) {
      ExtendsListFix.modifyList(otherList, false, -1, psiClassType);
    }
  }
}