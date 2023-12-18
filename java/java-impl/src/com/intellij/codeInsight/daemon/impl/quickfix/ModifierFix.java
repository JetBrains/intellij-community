// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.classlayout.FinalMethodInFinalClassInspection;
import com.siyeh.ig.classlayout.ProtectedMemberInFinalClassInspection;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ModifierFix extends PsiBasedModCommandAction<PsiModifierListOwner> {
  private static final Logger LOG = Logger.getInstance(ModifierFix.class);

  @PsiModifier.ModifierConstant private final String myModifier;
  private final boolean myShouldHave;
  private final boolean myShowContainingClass;
  private final @NotNull ThreeState myProcessHierarchy;
  private final @IntentionName String myName;

  public ModifierFix(@NotNull PsiModifierList modifierList,
                     @PsiModifier.ModifierConstant @NotNull String modifier,
                     boolean shouldHave,
                     boolean showContainingClass) {
    super((PsiModifierListOwner)modifierList.getParent());
    myModifier = modifier;
    myShouldHave = shouldHave;
    myShowContainingClass = showContainingClass;
    myName = format(null, modifierList, myShowContainingClass);
    myProcessHierarchy = ThreeState.UNSURE;
  }

  public ModifierFix(@NotNull PsiModifierListOwner owner,
                     @PsiModifier.ModifierConstant @NotNull String modifier,
                     boolean shouldHave,
                     boolean showContainingClass) {
    this(owner, modifier, shouldHave, showContainingClass, ThreeState.UNSURE);
  }

  public ModifierFix(@NotNull PsiModifierListOwner owner,
                     @PsiModifier.ModifierConstant @NotNull String modifier,
                     boolean shouldHave,
                     boolean showContainingClass, @NotNull ThreeState processHierarchy) {
    super(owner);
    myModifier = modifier;
    myShouldHave = shouldHave;
    myShowContainingClass = showContainingClass;
    myProcessHierarchy = processHierarchy;
    PsiVariable variable = owner instanceof PsiVariable ? (PsiVariable)owner : null;
    myName = format(variable, owner.getModifierList(), myShowContainingClass);
  }

  private @IntentionName @NotNull String format(PsiVariable variable, PsiModifierList modifierList, boolean showContainingClass) {
    String name = null;
    PsiElement parent = variable != null ? variable : modifierList != null ? modifierList.getParent() : null;
    if (parent instanceof PsiClass psiClass) {
      name = psiClass.getName();
    }
    else if (parent instanceof PsiJavaModule module) {
      name = module.getName();
    }
    else if (parent instanceof PsiMethod method) {
      int options = PsiFormatUtilBase.SHOW_NAME | (showContainingClass ? PsiFormatUtilBase.SHOW_CONTAINING_CLASS : 0);
      name = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, options, 0);
    }
    else if (parent instanceof PsiVariable var) {
      int options = PsiFormatUtilBase.SHOW_NAME | (showContainingClass ? PsiFormatUtilBase.SHOW_CONTAINING_CLASS : 0);
      name = PsiFormatUtil.formatVariable(var, options, PsiSubstitutor.EMPTY);
    }
    else if (parent instanceof PsiClassInitializer initializer) {
      PsiClass containingClass = initializer.getContainingClass();
      String className = containingClass instanceof PsiAnonymousClass
                         ? QuickFixBundle.message("anonymous.class.presentation",
                                                  ((PsiAnonymousClass)containingClass).getBaseClassType().getPresentableText())
                         : containingClass != null ? containingClass.getName() : "unknown";
      name = QuickFixBundle.message("class.initializer.presentation", className);
    }
    else if (parent instanceof PsiRequiresStatement requiresStatement) {
      name = "requires " + requiresStatement.getModuleName();
    }

    String modifierText = VisibilityUtil.toPresentableText(myModifier);
    if (!myShouldHave && modifierList != null) {
      int count = 0;
      for (PsiElement child = modifierList.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child.getText().equals(myModifier)) count++;
      }
      if (count > 1) {
        return QuickFixBundle.message("remove.one.modifier.fix", modifierText);
      }
    }
    return QuickFixBundle.message(myShouldHave ? "add.modifier.fix" : "remove.modifier.fix", name, modifierText);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return myShouldHave ? QuickFixBundle.message("add.modifier.fix.family", myModifier)
                        : QuickFixBundle.message("remove.modifier.fix.family", myModifier);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiModifierListOwner element) {
    final PsiModifierList modifierList = element.getModifierList();
    if (modifierList == null) return null;
    PsiFile containingFile = modifierList.getContainingFile();
    if (containingFile == null) return null;
    if (containingFile.getVirtualFile() == null) return null;
    PsiVariable variable = ObjectUtils.tryCast(element, PsiVariable.class);
    boolean isAvailable = modifierList.hasExplicitModifier(myModifier) != myShouldHave && !(variable instanceof SyntheticElement);
    if (!isAvailable) return null;

    String name = myName;
    if (myShowContainingClass) {
      PsiElement elementUnderCaret = context.findLeaf();
      if (elementUnderCaret != null && PsiTreeUtil.isAncestor(element, elementUnderCaret, false)) {
        name = format(variable, modifierList, false);
      }
    }

    return switch (myProcessHierarchy) {
      case YES -> Presentation.of(QuickFixBundle.message("fix.update.modifier.change.inheritors"));
      case NO -> Presentation.of(QuickFixBundle.message("fix.update.modifier.change.this"));
      case UNSURE -> Presentation.of(name).withFixAllOption(this,
                                                            action -> action instanceof ModifierFix modifierFix &&
                                                                      modifierFix.myModifier.equals(myModifier) &&
                                                                      modifierFix.myShouldHave == myShouldHave);
    };
  }

  private void changeModifierList (@NotNull PsiModifierList modifierList) {
    try {
      boolean needRemoveWhiteSpace = modifierList.getLastChild() instanceof PsiAnnotation &&
                                     modifierList.getNextSibling() instanceof PsiWhiteSpace &&
                                     myShouldHave;
      modifierList.setModifierProperty(myModifier, myShouldHave);
      if (needRemoveWhiteSpace) {
        modifierList.getNextSibling().delete();
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiModifierListOwner owner) {
    boolean shouldSearch = myProcessHierarchy != ThreeState.NO &&
                           owner instanceof PsiMethod && AccessModifier.fromPsiModifier(myModifier) != null;
    if (!shouldSearch) {
      return ModCommand.psiUpdate(owner, this::updateModifier);
    }
    return updateAccessInHierarchy((PsiMethod)owner);
  }

  protected void updateModifier(PsiModifierListOwner owner) {
    PsiVariable variable = ObjectUtils.tryCast(owner, PsiVariable.class);
    PsiModifierList modifierList;
    if (variable != null && variable.isValid()) {
      variable.normalizeDeclaration();
      modifierList = variable.getModifierList();
    } else {
      modifierList = owner.getModifierList();
    }
    if (modifierList == null) return;
    changeModifierList(modifierList);
    if (myShouldHave && owner instanceof final PsiMethod method) {
      if (PsiModifier.ABSTRACT.equals(myModifier)) {
        final PsiClass aClass = method.getContainingClass();
        if (aClass != null && !aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
          PsiModifierList classModifierList = aClass.getModifierList();
          if (classModifierList != null) {
            changeModifierList(classModifierList);
          }
        }
      }
      else if (PsiModifier.PUBLIC.equals(myModifier) &&
               method.getBody() != null &&
               !method.hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && containingClass.isInterface()) {
          modifierList.setModifierProperty(PsiModifier.DEFAULT, true);
        }
      }
      else if (PsiModifier.STATIC.equals(myModifier)) {
        if (method.hasModifierProperty(PsiModifier.DEFAULT)) {
          modifierList.setModifierProperty(PsiModifier.DEFAULT, false);
        }
        else if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
          PsiUtil.setModifierProperty(method, PsiModifier.ABSTRACT, false);
          CreateFromUsageUtils.setupMethodBody(method);
        }
      }
    }
    else if (PsiModifier.FINAL.equals(myModifier) && owner instanceof PsiClass aClass) {
      adjustVisibilityOfProtectedMembers(aClass);
      removeFinalModifierFromMethods(aClass);
    }
  }

  private static void adjustVisibilityOfProtectedMembers(PsiClass aClass) {
    StreamEx.<PsiMember>of(aClass.getMethods()).append(aClass.getFields()).forEach(member -> {
      if (!member.hasModifierProperty(PsiModifier.PROTECTED)) return;
      if (member instanceof PsiMethod method && !method.isConstructor() &&
          !PsiSuperMethodImplUtil.getHierarchicalMethodSignature(method).getSuperSignatures().isEmpty()) {
        return;
      }
      PsiModifierList memberModifierList = member.getModifierList();
      if (memberModifierList == null) return;
      boolean canBePrivate = ProtectedMemberInFinalClassInspection.canBePrivate(member, memberModifierList);
      memberModifierList.setModifierProperty(canBePrivate ? PsiModifier.PRIVATE : PsiModifier.PACKAGE_LOCAL, true);
    });
  }

  private static void removeFinalModifierFromMethods(PsiClass aClass) {
    StreamEx.of(aClass.getMethods()).forEach(method -> {
      if (FinalMethodInFinalClassInspection.isApplicableFor(method)) {
        method.getModifierList().setModifierProperty(PsiModifier.FINAL, false);
      }
    });
  }

  private @NotNull ModCommand updateAccessInHierarchy(@NotNull PsiMethod method) {
    PsiModifierList modifierList = method.getModifierList();
    assert modifierList != null;
    PsiModifierList copy = (PsiModifierList)modifierList.copy();
    changeModifierList(copy);
    final int accessLevel = PsiUtil.getAccessLevel(copy);
    if (accessLevel == PsiUtil.getAccessLevel(modifierList)) return ModCommand.nop();
    final List<PsiModifierList> modifierLists = new ArrayList<>();
    OverridingMethodsSearch.search(method, method.getResolveScope(), true).forEach(
      new PsiElementProcessorAdapter<>(new PsiElementProcessor<>() {
        @Override
        public boolean execute(@NotNull PsiMethod inheritor) {
          PsiModifierList list = inheritor.getModifierList();
          if (BaseIntentionAction.canModify(inheritor) && PsiUtil.getAccessLevel(list) < accessLevel) {
            modifierLists.add(list);
          }
          return true;
        }
      }));
    if (modifierLists.isEmpty()) {
      return ModCommand.psiUpdate(method, this::updateModifier);
    }
    if (myProcessHierarchy == ThreeState.YES) {
      return ModCommand.psiUpdate(method, (writableMethod, updater) -> {
        for (PsiModifierList list : ContainerUtil.map(modifierLists, updater::getWritable)) {
          changeModifierList(list);
        }
        updateModifier(writableMethod);
      });
    } else {
      return ModCommand.chooseAction(getFamilyName(),
                                     new ModifierFix(method, myModifier, myShouldHave, myShowContainingClass, ThreeState.YES),
                                     new ModifierFix(method, myModifier, myShouldHave, myShowContainingClass, ThreeState.NO));
    }
  }
}