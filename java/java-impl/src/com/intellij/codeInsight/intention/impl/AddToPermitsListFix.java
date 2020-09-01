// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.SealedUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

import static com.intellij.util.ObjectUtils.tryCast;

public class AddToPermitsListFix extends LocalQuickFixAndIntentionActionOnPsiElement {

  private final String myParentQualifiedName;
  private final String myParentName;
  private final String myClassName;

  public AddToPermitsListFix(@NotNull PsiClass subClass, @NotNull PsiClass superClass) {
    super(subClass);
    myParentQualifiedName = Objects.requireNonNull(superClass.getQualifiedName());
    myParentName = Objects.requireNonNull(superClass.getName());
    myClassName = Objects.requireNonNull(subClass.getName());
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiClass psiClass = tryCast(startElement, PsiClass.class);
    if (psiClass == null) return;
    String qualifiedName = psiClass.getQualifiedName();
    if (qualifiedName == null) return;
    PsiClass parentClass = findParent(psiClass.getExtendsListTypes());
    if (parentClass == null) parentClass = findParent(psiClass.getImplementsListTypes());
    if (parentClass == null) return;
    SealedUtils.fillPermitsList(parentClass, Collections.singleton(qualifiedName));
  }

  private PsiClass findParent(PsiClassType[] types) {
    return Arrays.stream(types).map(t -> t.resolve())
      .filter(parent -> parent != null && myParentQualifiedName.equals(parent.getQualifiedName()))
      .findFirst().orElse(null);
  }

  @Override
  public @IntentionName @NotNull String getText() {
    return JavaBundle.message("add.to.permits.list", myClassName, myParentName);
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return getText();
  }
}
