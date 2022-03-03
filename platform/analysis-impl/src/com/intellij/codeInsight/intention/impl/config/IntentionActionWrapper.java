// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionBean;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.actionSystem.ShortcutProvider;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IntentionActionWrapper implements IntentionAction, ShortcutProvider, IntentionActionDelegate, PossiblyDumbAware,
                                                     Comparable<IntentionAction> {
  private final IntentionActionBean extension;
  private String fullFamilyName;
  private @IntentionFamilyName String familyName;

  public IntentionActionWrapper(@NotNull IntentionActionBean extension) {
    this.extension = extension;
  }

  public @NotNull String getDescriptionDirectoryName() {
    return getDescriptionDirectoryName(getImplementationClassName());
  }

  static @NotNull String getDescriptionDirectoryName(@NotNull String fqn) {
    return fqn.substring(fqn.lastIndexOf('.') + 1).replaceAll("\\$", "");
  }

  @Override
  public @NotNull String getText() {
    return getDelegate().getText();
  }

  @Override
  public @NotNull String getFamilyName() {
    String result = familyName;
    if (result == null) {
      familyName = result = getDelegate().getFamilyName();
    }
    return result;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return getDelegate().isAvailable(project, editor, file);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    getDelegate().invoke(project, editor, file);
  }

  @Override
  public boolean startInWriteAction() {
    return getDelegate().startInWriteAction();
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return getDelegate().generatePreview(project, editor, file);
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return getDelegate().getElementToMakeWritable(file);
  }

  public @NotNull String getFullFamilyName() {
    String result = fullFamilyName;
    if (result == null) {
      String[] categories = extension.getCategories();
      fullFamilyName = result = categories != null ? String.join("/", categories) + "/" + getFamilyName() : getFamilyName();
    }
    return result;
  }

  @Override
  public boolean isDumbAware() {
    return DumbService.isDumbAware(getDelegate());
  }

  @Override
  public @NotNull IntentionAction getDelegate() {
    return extension.getInstance();
  }

  @Override
  public @NotNull String getImplementationClassName() {
    return extension.className;
  }

  @NotNull ClassLoader getImplementationClassLoader() {
    return extension.getLoaderForClass();
  }

  @Override
  public String toString() {
    String text;
    try {
      text = getText();
    }
    catch (PsiInvalidElementAccessException e) {
      text = e.getMessage();
    }
    return "Intention: (" + getDelegate().getClass() + "): '" + text + "'";
  }

  @Override
  public boolean equals(Object obj) {
    return super.equals(obj) || getDelegate().equals(obj);
  }

  @Override
  public @Nullable ShortcutSet getShortcut() {
    IntentionAction delegate = getDelegate();
    return delegate instanceof ShortcutProvider ? ((ShortcutProvider)delegate).getShortcut() : null;
  }

  @Override
  public int compareTo(@NotNull IntentionAction other) {
    if (other instanceof IntentionActionWrapper) {
      IntentionAction action1 = getDelegate();
      IntentionAction action2 = ((IntentionActionWrapper)other).getDelegate();
      if (action1 instanceof Comparable && action2 instanceof Comparable) {
        //noinspection rawtypes,unchecked
        return ((Comparable)action1).compareTo(action2);
      }
    }
    return getText().compareTo(other.getText());
  }
}
