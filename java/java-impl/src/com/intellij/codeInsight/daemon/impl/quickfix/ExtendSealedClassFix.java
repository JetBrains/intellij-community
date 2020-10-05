// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.intellij.util.ObjectUtils.tryCast;

public class ExtendSealedClassFix implements DefaultIntentionActionWithChoice {

  private static final String[] SUBCLASS_MODIFIERS = {PsiModifier.FINAL, PsiModifier.NON_SEALED, PsiModifier.SEALED};

  private final boolean myParentIsInterface;
  private final boolean myChildIsInterface;
  private final SmartPsiElementPointer<PsiJavaCodeReferenceElement> mySubclassReferencePointer;

  private ExtendSealedClassFix(PsiJavaCodeReferenceElement reference, boolean parentIsInterface, boolean childIsInterface) {
    mySubclassReferencePointer = SmartPointerManager.createPointer(reference);
    myParentIsInterface = parentIsInterface;
    myChildIsInterface = childIsInterface;
  }

  @Override
  public @NotNull ChoiceTitleIntentionAction getTitle() {
    String fixName = getName();
    return new ChoiceTitleIntentionAction(fixName, fixName);
  }

  @NotNull
  @Nls
  private String getName() {
    return QuickFixBundle.message(myParentIsInterface ? "implement.sealed.title" : "extend.sealed.title");
  }

  @Override
  public @NotNull List<ChoiceVariantIntentionAction> getVariants() {
    if (!myParentIsInterface && myChildIsInterface) return Collections.emptyList();
    if (myChildIsInterface) {
      return Arrays.asList(new ExtendSealedClassVariantAction(0, PsiModifier.SEALED),
                           new ExtendSealedClassVariantAction(1, PsiModifier.NON_SEALED));
    }
    return IntStream.range(0, SUBCLASS_MODIFIERS.length)
      .mapToObj(i -> new ExtendSealedClassVariantAction(i, SUBCLASS_MODIFIERS[i]))
      .collect(Collectors.toList());
  }

  /**
   * @return fixes or null if given case is not supported (but probably valid)
   */
  static LocalQuickFix @Nullable [] createFixes(@NotNull PsiJavaCodeReferenceElement subclassReference,
                                                @NotNull PsiClass parentClass,
                                                @NotNull PsiClass subclass) {
    if (!parentClass.hasModifierProperty(PsiModifier.SEALED)) return null;
    boolean parentIsInterface = parentClass.isInterface();
    if (parentIsInterface && (subclass.isRecord() || subclass.isEnum())) return null;
    PsiModifierList modifiers = subclass.getModifierList();
    if (modifiers == null || hasSealedClassSubclassModifier(modifiers)) return null;
    ExtendSealedClassFix extendSealedClassFix = new ExtendSealedClassFix(subclassReference, parentIsInterface, subclass.isInterface());
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
    return ContainerUtil.exists(SUBCLASS_MODIFIERS, m -> modifiers.hasExplicitModifier(m));
  }

  private class ExtendSealedClassVariantAction extends ChoiceVariantIntentionAction {

    private final int myIndex;
    private final @NlsSafe String myModifier;

    private ExtendSealedClassVariantAction(int index, @NotNull String modifier) {
      myIndex = index;
      myModifier = modifier;
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
      return ExtendSealedClassFix.this.getName();
    }

    @Override
    public @NlsContexts.Tooltip String getTooltipText() {
      return getFamilyName() + " " + myModifier;
    }

    @Override
    public void applyFix(@NotNull Project project, PsiFile file, @Nullable Editor editor) {
      if (editor == null) return;
      PsiJavaCodeReferenceElement referenceElement = mySubclassReferencePointer.getElement();
      if (referenceElement == null) return;
      PsiClass parentClass = PsiTreeUtil.getParentOfType(referenceElement, PsiClass.class);
      if (parentClass == null || !parentClass.hasModifierProperty(PsiModifier.SEALED)) return;
      PsiClass subclass = tryCast(referenceElement.resolve(), PsiClass.class);
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
  }
}
