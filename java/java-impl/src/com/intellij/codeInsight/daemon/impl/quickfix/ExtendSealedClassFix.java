// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.modcommand.*;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ExtendSealedClassFix extends PsiBasedModCommandAction<PsiClass> {
  private final SmartPsiElementPointer<PsiClass> myParentClassPointer;

  private ExtendSealedClassFix(@NotNull PsiClass parentClass, @NotNull PsiClass subclass) {
    super(subclass);
    myParentClassPointer = SmartPointerManager.createPointer(parentClass);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("implement.or.extend.fix.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiClass subclass) {
    PsiClass parentClass = myParentClassPointer.getElement();
    if (parentClass == null) return null;
    int extendsImplements = subclass.isInterface() || !parentClass.isInterface() ? 1 : 2;
    String name = QuickFixBundle.message("extend.sealed.name", subclass.getName(), extendsImplements, parentClass.getName());
    return Presentation.of(name);
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiClass subclass) {
    PsiClass parentClass = myParentClassPointer.getElement();
    if (parentClass == null) return ModCommand.nop();
    boolean parentIsInterface = parentClass.isInterface();
    boolean subclassIsInterface = subclass.isInterface();
    if (!parentIsInterface && subclassIsInterface) return ModCommand.nop();
    List<ModCommandAction> fixes;
    if (subclassIsInterface) {
      fixes = List.of(new ExtendSealedClassVariantAction(PsiModifier.SEALED, myParentClassPointer, subclass),
                      new ExtendSealedClassVariantAction(PsiModifier.NON_SEALED, myParentClassPointer, subclass));
    }
    else {
      fixes = List.of(new ExtendSealedClassVariantAction(PsiModifier.FINAL, myParentClassPointer, subclass),
                      new ExtendSealedClassVariantAction(PsiModifier.SEALED, myParentClassPointer, subclass),
                      new ExtendSealedClassVariantAction(PsiModifier.NON_SEALED, myParentClassPointer, subclass));
    }
    int extendsImplements = subclass.isInterface() || !parentClass.isInterface() ? 1 : 2;
    //noinspection DialogTitleCapitalization
    String name = QuickFixBundle.message("extend.sealed.title", subclass.getName(), extendsImplements, parentClass.getName());
    return ModCommand.chooseAction(name, fixes);
  }

  /**
   * @return fixes or null if given case is not supported (but probably valid)
   */
  static @Nullable ModCommandAction createFix(@NotNull PsiClass parentClass, @NotNull PsiClass subclass) {
    if (!parentClass.hasModifierProperty(PsiModifier.SEALED) || !parentClass.getManager().isInProject(parentClass)) return null;
    boolean parentIsInterface = parentClass.isInterface();
    if (parentIsInterface && (subclass.isRecord() || subclass.isEnum())) return null;
    PsiModifierList modifiers = subclass.getModifierList();
    if (modifiers == null || hasSealedClassSubclassModifier(modifiers)) return null;
    return new ExtendSealedClassFix(parentClass, subclass);
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

  private static class ExtendSealedClassVariantAction extends PsiUpdateModCommandAction<PsiClass> {
    private final @NlsSafe String myModifier;
    private final SmartPsiElementPointer<PsiClass> myParentClassPointer;

    private ExtendSealedClassVariantAction(@NotNull String modifier,
                                           SmartPsiElementPointer<PsiClass> parentClassPointer,
                                           PsiClass subclass) {
      super(subclass);
      myModifier = modifier;
      myParentClassPointer = parentClassPointer;
    }

    @Override
    protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiClass element) {
      return Presentation.of(myModifier);
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return QuickFixBundle.message("implement.or.extend.fix.family");
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiClass subclass, @NotNull ModPsiUpdater updater) {
      PsiClass parentClass = myParentClassPointer.getElement();
      if (parentClass == null || !parentClass.hasModifierProperty(PsiModifier.SEALED)) return;
      if (ImplementOrExtendFix.implementOrExtend(parentClass, subclass) == null) return;
      PsiModifierList modifiers = subclass.getModifierList();
      if (modifiers == null) return;
      if (hasSealedClassSubclassModifier(modifiers)) return;
      modifiers.setModifierProperty(myModifier, true);
      Query<PsiClass> subclassInheritors = DirectClassInheritorsSearch.search(subclass);
      if (PsiModifier.FINAL.equals(myModifier) && subclassInheritors.findFirst() != null ||
          PsiModifier.SEALED.equals(myModifier) && subclassInheritors.anyMatch(child -> !hasSealedClassSubclassModifier(child))) {
        updater.moveCaretTo(subclass);
      }
    }
  }
}
