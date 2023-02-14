// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.choice.ChoiceTitleIntentionAction;
import com.intellij.codeInsight.intention.choice.ChoiceVariantIntentionAction;
import com.intellij.codeInsight.intention.choice.DefaultIntentionActionWithChoice;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ExtendSealedClassFix implements DefaultIntentionActionWithChoice {
  @FileModifier.SafeFieldForPreview private final SmartPsiElementPointer<PsiClass> myParentClassPointer;
  @FileModifier.SafeFieldForPreview private final SmartPsiElementPointer<PsiClass> mySubclassPointer;
  private final @Nls String myName;

  private ExtendSealedClassFix(PsiClass parentClass, PsiClass subclass) {
    myParentClassPointer = SmartPointerManager.createPointer(parentClass);
    mySubclassPointer = SmartPointerManager.createPointer(subclass);
    int extendsImplements = subclass.isInterface() || !parentClass.isInterface() ? 1 : 2;
    myName = QuickFixBundle.message("extend.sealed.title", subclass.getName(), extendsImplements, parentClass.getName());
  }

  @Override
  public @NotNull ChoiceTitleIntentionAction getTitle() {
    return new ChoiceTitleIntentionAction(QuickFixBundle.message("implement.or.extend.fix.family"), myName);
  }

  @Override
  public @NotNull List<@NotNull ChoiceVariantIntentionAction> getVariants() {
    PsiClass subclass = mySubclassPointer.getElement();
    PsiClass parentClass = myParentClassPointer.getElement();
    if (subclass == null || parentClass == null) return Collections.emptyList();
    boolean parentIsInterface = parentClass.isInterface();
    boolean subclassIsInterface = subclass.isInterface();
    if (!parentIsInterface && subclassIsInterface) return Collections.emptyList();
    if (subclassIsInterface) {
      return Arrays.asList(new ExtendSealedClassVariantAction(0, PsiModifier.SEALED, myParentClassPointer, mySubclassPointer),
                           new ExtendSealedClassVariantAction(1, PsiModifier.NON_SEALED, myParentClassPointer, mySubclassPointer));
    }
    return Arrays.asList(new ExtendSealedClassVariantAction(0, PsiModifier.FINAL, myParentClassPointer, mySubclassPointer),
                         new ExtendSealedClassVariantAction(1, PsiModifier.SEALED, myParentClassPointer, mySubclassPointer),
                         new ExtendSealedClassVariantAction(2, PsiModifier.NON_SEALED, myParentClassPointer, mySubclassPointer));
  }

  /**
   * @return fixes or null if given case is not supported (but probably valid)
   */
  static LocalQuickFix @Nullable [] createFixes(@NotNull PsiClass parentClass, @NotNull PsiClass subclass) {
    if (!parentClass.hasModifierProperty(PsiModifier.SEALED) || !parentClass.getManager().isInProject(parentClass)) return null;
    boolean parentIsInterface = parentClass.isInterface();
    if (parentIsInterface && (subclass.isRecord() || subclass.isEnum())) return null;
    PsiModifierList modifiers = subclass.getModifierList();
    if (modifiers == null || hasSealedClassSubclassModifier(modifiers)) return null;
    ExtendSealedClassFix extendSealedClassFix = new ExtendSealedClassFix(parentClass, subclass);
    List<LocalQuickFix> actions = new ArrayList<>();
    actions.add(extendSealedClassFix.getTitle());
    actions.addAll(extendSealedClassFix.getVariants());
    return actions.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  private static boolean hasSealedClassSubclassModifier(PsiClass psiClass) {
    PsiModifierList modifiers = psiClass.getModifierList();
    if (modifiers == null) return false;
    return hasSealedClassSubclassModifier(modifiers);
  }

  private static boolean hasSealedClassSubclassModifier(@NotNull PsiModifierList modifiers) {
    return modifiers.hasExplicitModifier(PsiModifier.FINAL) ||
           modifiers.hasExplicitModifier(PsiModifier.SEALED) ||
           modifiers.hasExplicitModifier(PsiModifier.NON_SEALED);
  }

  private static class ExtendSealedClassVariantAction extends ChoiceVariantIntentionAction {

    private final int myIndex;
    private final @NlsSafe String myModifier;
    private final SmartPsiElementPointer<PsiClass> myParentClassPointer;
    private final SmartPsiElementPointer<PsiClass> mySubclassPointer;

    private ExtendSealedClassVariantAction(int index, @NotNull String modifier,
                                           SmartPsiElementPointer<PsiClass> parentClassPointer,
                                           SmartPsiElementPointer<PsiClass> subclassPointer) {
      myIndex = index;
      myModifier = modifier;
      myParentClassPointer = parentClassPointer;
      mySubclassPointer = subclassPointer;
    }

    @Override
    public int getIndex() {
      return myIndex;
    }

    @Override
    public @IntentionName @NotNull String getName() {
      return myModifier;
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return QuickFixBundle.message("implement.or.extend.fix.family");
    }

    @Override
    public @NlsContexts.Tooltip String getTooltipText() {
      return getFamilyName() + " " + myModifier;
    }

    @Override
    public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
      return mySubclassPointer.getContainingFile();
    }

    @Override
    public void applyFix(@NotNull Project project, PsiFile file, @Nullable Editor editor) {
      PsiClass parentClass = myParentClassPointer.getElement();
      if (parentClass == null || !parentClass.hasModifierProperty(PsiModifier.SEALED)) return;
      PsiClass subclass = mySubclassPointer.getElement();
      if (subclass == null) return;
      if (ImplementOrExtendFix.implementOrExtend(parentClass, subclass) == null) return;
      PsiModifierList modifiers = subclass.getModifierList();
      if (modifiers == null) return;
      if (hasSealedClassSubclassModifier(modifiers)) return;
      modifiers.setModifierProperty(myModifier, true);
      Query<PsiClass> subclassInheritors = DirectClassInheritorsSearch.search(subclass);
      if (PsiModifier.FINAL.equals(myModifier) && subclassInheritors.findFirst() != null ||
          PsiModifier.SEALED.equals(myModifier) && subclassInheritors.anyMatch(child -> !hasSealedClassSubclassModifier(child))) {
        subclass.navigate(true);
      }
    }

    @Override
    public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
      PsiClass subclass = mySubclassPointer.getElement();
      PsiClass copy = PsiTreeUtil.findSameElementInCopy(subclass, target);
      if (copy == null) return null;
      return new ExtendSealedClassVariantAction(myIndex, myModifier, myParentClassPointer, SmartPointerManager.createPointer(copy));
    }
  }
}
