// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ImplementOrExtendFix extends LocalQuickFixAndIntentionActionOnPsiElement {

  @SafeFieldForPreview private final SmartPsiElementPointer<PsiClass> mySubclassPointer;
  @SafeFieldForPreview private final SmartPsiElementPointer<PsiClass> myParentClassPointer;
  private final boolean myOnTheFly;
  private final @IntentionName String myName;

  private ImplementOrExtendFix(@NotNull PsiElement place, @NotNull PsiClass subclass, @NotNull PsiClass parentClass, boolean onTheFly) {
    super(place);
    mySubclassPointer = SmartPointerManager.createPointer(subclass);
    myParentClassPointer = SmartPointerManager.createPointer(parentClass);
    myOnTheFly = onTheFly;
    myName = parentClass.isInterface() && !subclass.isInterface()
             ? QuickFixBundle.message("implement.or.extend.fix.implement.text", subclass.getName(), parentClass.getName())
             : QuickFixBundle.message("implement.or.extend.fix.extend.text", subclass.getName(), parentClass.getName());
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    // can happen during batch-inspection if resolution has already been applied
    // to plugin.xml or java class
    PsiClass subclass = mySubclassPointer.getElement();
    if (subclass == null || !subclass.isValid()) return;
    PsiClass parentClass = myParentClassPointer.getElement();
    if (parentClass == null) return;
    boolean external = file != subclass.getContainingFile();

    PsiElement e = implementOrExtend(parentClass, subclass);
    if (myOnTheFly && external && e instanceof Navigatable) ((Navigatable)e).navigate(true);
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return mySubclassPointer.getElement();
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    PsiClass subclass = mySubclassPointer.getElement();
    PsiClass copy = PsiTreeUtil.findSameElementInCopy(subclass, target);
    PsiElement place = myStartElement.getElement();
    PsiClass parentClass = myParentClassPointer.getElement();
    if (copy == null || place == null || parentClass == null) return null;
    return new ImplementOrExtendFix(place, copy, parentClass, myOnTheFly);
  }

  @Override
  public @IntentionName @NotNull String getText() {
    return myName;
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return QuickFixBundle.message("implement.or.extend.fix.family");
  }

  public static IntentionAction[] createActions(@NotNull PsiElement place, @NotNull PsiClass subclass,
                                                @NotNull PsiClass parentClass, boolean onTheFly) {
    return ContainerUtil.map2Array(createFixes(place, subclass, parentClass, onTheFly), IntentionAction.class, f -> (IntentionAction)f);
  }

  public static LocalQuickFix @NotNull [] createFixes(@NotNull PsiElement place, @NotNull PsiClass subclass,
                                                      @NotNull PsiClass parentClass, boolean onTheFly) {
    if (!parentClass.isInterface() && (subclass.isInterface() || subclass.isRecord() || subclass.isEnum())) {
      return LocalQuickFix.EMPTY_ARRAY;
    }
    if (subclass.isAnnotationType()) return LocalQuickFix.EMPTY_ARRAY;
    PsiModifierList modifiers = subclass.getModifierList();
    if (modifiers == null) return LocalQuickFix.EMPTY_ARRAY;
    if (parentClass.isInterface()) {
      PsiReferenceList targetList = subclass.isInterface() ? subclass.getExtendsList() : subclass.getImplementsList();
      if (targetList == null) return LocalQuickFix.EMPTY_ARRAY;
    }
    else if (subclass.getExtendsList() == null || hasNonObjectParent(subclass)) {
      return LocalQuickFix.EMPTY_ARRAY;
    }

    LocalQuickFix[] fixes = ExtendSealedClassFix.createFixes(parentClass, subclass);
    if (fixes != null) return fixes;
    return new LocalQuickFix[]{ new ImplementOrExtendFix(place, subclass, parentClass, onTheFly) };
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
    return targetList.add(parentReference);
  }
}
