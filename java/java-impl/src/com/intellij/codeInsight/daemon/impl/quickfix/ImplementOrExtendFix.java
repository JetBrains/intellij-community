// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.*;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ImplementOrExtendFix extends PsiUpdateModCommandAction<PsiClass> {

  private final SmartPsiElementPointer<PsiClass> myParentClassPointer;
  private final @IntentionName String myName;
  private final boolean myExternal;

  private ImplementOrExtendFix(@NotNull PsiClass subclass, @NotNull PsiClass parentClass) {
    super(subclass);
    myExternal = subclass.getContainingFile() != parentClass.getContainingFile();
    myParentClassPointer = SmartPointerManager.createPointer(parentClass);
    myName = parentClass.isInterface() && !subclass.isInterface()
             ? QuickFixBundle.message("implement.or.extend.fix.implement.text", subclass.getName(), parentClass.getName())
             : QuickFixBundle.message("implement.or.extend.fix.extend.text", subclass.getName(), parentClass.getName());
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiClass subclass, @NotNull ModPsiUpdater updater) {
    PsiClass parentClass = myParentClassPointer.getElement();
    if (parentClass == null) return;

    PsiElement e = implementOrExtend(parentClass, subclass);
    if (myExternal && e != null) {
      updater.moveCaretTo(e);
    }
  }

  @Override
  protected @NotNull Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiClass element) {
    return Presentation.of(myName);
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return QuickFixBundle.message("implement.or.extend.fix.family");
  }

  public static @Nullable ModCommandAction createFix(@NotNull PsiClass subclass,
                                                      @NotNull PsiClass parentClass) {
    if (!parentClass.isInterface() && (subclass.isInterface() || subclass.isRecord() || subclass.isEnum())) {
      return null;
    }
    if (subclass.isAnnotationType()) return null;
    PsiModifierList modifiers = subclass.getModifierList();
    if (modifiers == null) return null;
    if (parentClass.isInterface()) {
      PsiReferenceList targetList = subclass.isInterface() ? subclass.getExtendsList() : subclass.getImplementsList();
      if (targetList == null) return null;
    }
    else if (subclass.getExtendsList() == null || hasNonObjectParent(subclass)) {
      return null;
    }

    ModCommandAction fix = ExtendSealedClassFix.createFix(parentClass, subclass);
    if (fix != null) return fix;
    return new ImplementOrExtendFix(subclass, parentClass);
  }

  static boolean hasNonObjectParent(@NotNull PsiClass psiClass) {
    PsiClassType[] extendsListTypes = psiClass.getExtendsListTypes();
    if (extendsListTypes.length == 0) return false;
    if (extendsListTypes.length == 1 && TypeUtils.isJavaLangObject(extendsListTypes[0])) return false;
    return true;
  }

  static @Nullable PsiElement implementOrExtend(@NotNull PsiClass parentClass, @NotNull PsiClass subclass) {
    boolean subclassIsInterface = subclass.isInterface();
    boolean parentIsInterface = parentClass.isInterface();
    if (!parentIsInterface && subclassIsInterface) return null;
    final PsiReferenceList targetList;
    if (parentIsInterface && !subclassIsInterface) {
      PsiReferenceList implementsList = subclass.getImplementsList();
      if (implementsList == null) return null;
      targetList = implementsList;
    }
    else {
      PsiReferenceList extendsList = subclass.getExtendsList();
      if (extendsList == null || !subclassIsInterface && hasNonObjectParent(subclass)) return null;
      PsiJavaCodeReferenceElement[] parents = extendsList.getReferenceElements();
      if (parents.length > 0 && !subclassIsInterface) parents[0].delete();
      targetList = extendsList;
    }
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(parentClass.getProject()).getElementFactory();
    PsiJavaCodeReferenceElement parentReference = elementFactory.createReferenceElementByType(elementFactory.createType(parentClass));
    return JavaCodeStyleManager.getInstance(subclass.getProject()).shortenClassReferences(targetList.add(parentReference));
  }
}
