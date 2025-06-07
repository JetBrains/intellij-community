// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * changes 'class a extends b' to 'class a implements b' or vice versa
 */
public class ChangeExtendsToImplementsFix extends PsiBasedModCommandAction<PsiClass> {
  private final @NotNull SmartTypePointer myTypeToExtendFrom;

  public ChangeExtendsToImplementsFix(@NotNull PsiClass aClass, @NotNull PsiClassType classTypeToExtendFrom) {
    super(aClass);
    PsiClassType typeToExtendFrom = aClass instanceof PsiTypeParameter ? 
                                    classTypeToExtendFrom : (PsiClassType)GenericsUtil.eliminateWildcards(classTypeToExtendFrom);
    myTypeToExtendFrom = SmartTypePointerManager.getInstance(aClass.getProject()).createSmartTypePointer(typeToExtendFrom);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("change.extends.list.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiClass myClass) {
    PsiType typeToExtendFrom = myTypeToExtendFrom.getType();
    if (typeToExtendFrom == null) return null;
    PsiClass classToExtendFrom = PsiUtil.resolveClassInClassTypeOnly(typeToExtendFrom);
    boolean available = classToExtendFrom != null && classToExtendFrom.isValid()
                        && !classToExtendFrom.hasModifierProperty(PsiModifier.FINAL)
                        && (classToExtendFrom.isInterface() ||
                            !myClass.isInterface() && myClass.getExtendsList() != null
                            && myClass.getExtendsList().getReferencedTypes().length == 0);
    if (!available) return null;
    String name = QuickFixBundle.message(
      "exchange.extends.implements.keyword",
      myClass.isInterface() == classToExtendFrom.isInterface() ? JavaKeywords.IMPLEMENTS : JavaKeywords.EXTENDS,
      myClass.isInterface() == classToExtendFrom.isInterface() ? JavaKeywords.EXTENDS : JavaKeywords.IMPLEMENTS,
      classToExtendFrom.getName());
    return Presentation.of(name).withPriority(PriorityAction.Priority.HIGH);

  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiClass myClass) {
    if (!(myTypeToExtendFrom.getType() instanceof PsiClassType typeToExtendFrom)) return ModCommand.nop();
    return new ExtendsListModCommandFix(myClass, typeToExtendFrom, true).perform(context);
  }
}