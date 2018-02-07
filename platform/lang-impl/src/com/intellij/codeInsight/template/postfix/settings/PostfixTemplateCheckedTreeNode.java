// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.settings;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.ui.CheckedTreeNode;
import org.jetbrains.annotations.NotNull;

public final class PostfixTemplateCheckedTreeNode extends CheckedTreeNode {
  @NotNull
  private final String myLanguageName;
  @NotNull
  private final PostfixTemplate myTemplate;

  @NotNull
  public PostfixTemplate getTemplate() {
    return myTemplate;
  }

  @NotNull
  public String getLanguageName() {
    return myLanguageName;
  }

  PostfixTemplateCheckedTreeNode(@NotNull PostfixTemplate template, @NotNull String languageName) {
    super(template.getKey().replaceFirst("\\.", ""));
    myLanguageName = languageName;
    myTemplate = template;
  }
}
