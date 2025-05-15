// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionBean;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ex.ToolLanguageUtil;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.actionSystem.ShortcutProvider;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public final class IntentionActionWrapper implements IntentionAction, ShortcutProvider, IntentionActionDelegate, Comparable<IntentionAction> {
  private final IntentionActionBean extension;
  private IntentionAction instance;
  private String fullFamilyName;

  private volatile Set<String> applicableToLanguages;  // lazy initialized

  public IntentionActionWrapper(@NotNull IntentionActionBean extension) {
    this.extension = extension;
  }

  public @NotNull String getDescriptionDirectoryName() {
    return getDescriptionDirectoryName(getImplementationClassName());
  }

  @ApiStatus.Internal
  public static @NotNull String getDescriptionDirectoryName(@NotNull String fqn) {
    return fqn.substring(fqn.lastIndexOf('.') + 1).replaceAll("\\$", "");
  }

  @Override
  public @NotNull String getText() {
    return getDelegate().getText();
  }

  @Override
  public @NotNull String getFamilyName() {
    return getDelegate().getFamilyName();
  }

  public boolean isApplicable(@NotNull Collection<String> fileLanguageIds) {
    String language = extension.language;
    if (language == null
        || "any".equals(language)
        || language.isBlank()) {
      return true;
    }

    Set<String> languages = applicableToLanguages;
    if (languages == null) {
      applicableToLanguages = languages = ToolLanguageUtil.getAllMatchingLanguages(language, true);
    }
    return ContainerUtil.intersects(fileLanguageIds, languages);
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return getDelegate().isAvailable(project, editor, psiFile);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    getDelegate().invoke(project, editor, psiFile);
  }

  @Override
  public boolean startInWriteAction() {
    return getDelegate().startInWriteAction();
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    return getDelegate().generatePreview(project, editor, psiFile);
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
  public @NotNull IntentionAction getDelegate() {
    if (instance == null) {
      CommonIntentionAction base = extension.getInstance();
      instance = base instanceof IntentionAction action ? action :
                 base.asIntention();
    }
    return instance;
  }

  @Override
  public @NotNull String getImplementationClassName() {
    return extension.className;
  }

  @ApiStatus.Internal
  public @NotNull ClassLoader getImplementationClassLoader() {
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
    ModCommandAction modCommand = asModCommandAction();
    Class<?> cls = modCommand == null ? getDelegate().getClass() : modCommand.getClass();
    return "Intention: (" + cls.getName() + "): '" + text + "'";
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
