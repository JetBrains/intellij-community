// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.settings;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.ui.CheckedTreeNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PostfixTemplateCheckedTreeNode extends CheckedTreeNode {
  private final @NotNull PostfixTemplateProvider myTemplateProvider;
  private @NotNull PostfixTemplate myTemplate;

  private @Nullable PostfixTemplate myInitialTemplate;
  private final boolean myNew;

  public @NotNull PostfixTemplate getTemplate() {
    return myTemplate;
  }

  public @NotNull PostfixTemplateProvider getTemplateProvider() {
    return myTemplateProvider;
  }

  PostfixTemplateCheckedTreeNode(@NotNull PostfixTemplate template, @NotNull PostfixTemplateProvider templateProvider, boolean isNew) {
    super(template.getPresentableName());
    myTemplateProvider = templateProvider;
    myTemplate = template;
    myInitialTemplate = template;
    myNew = isNew;
  }

  public void setTemplate(@NotNull PostfixTemplate template) {
    if (myInitialTemplate == null) {
      myInitialTemplate = myTemplate;
    }
    myTemplate = template;
  }

  public boolean isChanged() {
    return myInitialTemplate != null && !myInitialTemplate.equals(myTemplate);
  }

  public boolean isNew() {
    return myNew;
  }

  @Override
  public String toString() {
    return myTemplate.getPresentableName();
  }
}
