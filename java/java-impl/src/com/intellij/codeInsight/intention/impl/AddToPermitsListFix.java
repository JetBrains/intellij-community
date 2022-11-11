// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.siyeh.ig.psiutils.SealedUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

import static com.intellij.util.ObjectUtils.tryCast;

public class AddToPermitsListFix extends LocalQuickFixAndIntentionActionOnPsiElement implements IntentionActionWithFixAllOption {

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
    PsiClass parentClass = getSealedParent(psiClass);
    if (parentClass == null) return;
    SealedUtils.addClassToPermitsList(parentClass, qualifiedName);
    PsiReferenceList list = parentClass.getPermitsList();
    if (list != null) {
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(list);
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (!(getStartElement() instanceof PsiClass psiClass)) return IntentionPreviewInfo.EMPTY;
    PsiClass parent = getSealedParent(psiClass);
    if (parent == null) return IntentionPreviewInfo.EMPTY;
    PsiFile sealedClassFile = parent.getContainingFile();
    if (sealedClassFile == psiClass.getContainingFile()) {
      return super.generatePreview(project, editor, file);
    }
    String qualifiedName = psiClass.getQualifiedName();
    if (qualifiedName == null) return IntentionPreviewInfo.EMPTY;
    PsiClass copy = PsiTreeUtil.findSameElementInCopy(parent, (PsiFile)sealedClassFile.copy());
    SealedUtils.addClassToPermitsList(copy, qualifiedName);
    CodeStyleManager.getInstance(project).reformat(Objects.requireNonNull(copy.getPermitsList()));
    return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, sealedClassFile.getName(), parent.getText(), copy.getText());
  }

  private @Nullable PsiClass getSealedParent(PsiClass psiClass) {
    PsiClass parentClass = findParent(psiClass.getExtendsListTypes());
    if (parentClass == null) parentClass = findParent(psiClass.getImplementsListTypes());
    if (parentClass == null) return null;
    return parentClass;
  }

  private @Nullable PsiClass findParent(PsiClassType[] types) {
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
    return JavaBundle.message("add.to.permits.list.family.name");
  }
}
