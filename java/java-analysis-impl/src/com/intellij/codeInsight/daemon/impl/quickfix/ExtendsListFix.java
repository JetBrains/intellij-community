// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

/**
 * @deprecated use {@link ExtendsListModCommandFix}
 */
@Deprecated
public class ExtendsListFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  // we don't modify this class
  @SafeFieldForPreview protected final @Nullable SmartPsiElementPointer<PsiClass> myClassToExtendFromPointer;
  private final boolean myToAdd;
  @SafeFieldForPreview // we don't modify PSI referenced from this type
  private final PsiClassType myTypeToExtendFrom;
  private final @IntentionName String myName;

  // Used in plugins
  @SuppressWarnings("unused")
  public ExtendsListFix(@NotNull PsiClass aClass, @NotNull PsiClassType typeToExtendFrom, boolean toAdd) {
    this(aClass, typeToExtendFrom.resolve(), typeToExtendFrom, toAdd);
  }

  // Used in plugins
  @SuppressWarnings("unused")
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
                             @NotNull PsiFile psiFile,
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
                     @NotNull PsiFile psiFile,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiClass myClass = (PsiClass)startElement;
    new ExtendsListModCommandFix(myClass, myTypeToExtendFrom, myToAdd).invokeImpl(myClass);
  }
}
