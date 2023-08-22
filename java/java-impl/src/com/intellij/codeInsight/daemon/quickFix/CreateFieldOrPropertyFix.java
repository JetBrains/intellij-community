// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.GenerateFieldOrPropertyHandler;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.GenerationInfo;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.ModTemplateBuilder;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyMemberType;
import com.intellij.psi.util.PropertyUtilBase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class CreateFieldOrPropertyFix extends PsiUpdateModCommandAction<PsiClass> {
  private final String myName;
  private final PsiType myType;
  private final PropertyMemberType myMemberType;
  private final PsiAnnotation[] myAnnotations;

  public CreateFieldOrPropertyFix(@NotNull PsiClass aClass,
                                  String name,
                                  PsiType type,
                                  @NotNull PropertyMemberType memberType,
                                  PsiAnnotation[] annotations) {
    super(aClass);
    myName = name;
    myType = type;
    myMemberType = memberType;
    myAnnotations = annotations;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message(myMemberType == PropertyMemberType.FIELD ? "create.field.text" : "create.property.text", myName);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiClass aClass, @NotNull ModPsiUpdater updater) {
    final PsiElement lBrace = aClass.getLBrace();
    int offset = (lBrace == null ? aClass : lBrace).getTextRange().getStartOffset();
    List<? extends GenerationInfo> prototypes = new GenerateFieldOrPropertyHandler(myName, myType, myMemberType, myAnnotations)
        .generateMemberPrototypes(aClass, ClassMember.EMPTY_ARRAY);
    prototypes = GenerateMembersUtil.insertMembersAtOffset(aClass, offset, prototypes);
    if (prototypes.isEmpty()) return;
    final PsiElement scope = Objects.requireNonNull(prototypes.get(0).getPsiMember()).getContext();
    assert scope != null;
    ModTemplateBuilder builder = updater.templateBuilder();
    boolean first = true;
    @NonNls final String TYPE_NAME_VAR = "TYPE_NAME_VAR";
    for (GenerationInfo prototype : prototypes) {
      final PsiTypeElement typeElement = PropertyUtilBase.getPropertyTypeElement(prototype.getPsiMember());
      if (typeElement == null) continue;
      if (first) {
        first = false;
        builder.field(typeElement, myType.getCanonicalText());
      }
      else {
        builder.field(typeElement, TYPE_NAME_VAR, TYPE_NAME_VAR, false);
      }
    }
  }
}