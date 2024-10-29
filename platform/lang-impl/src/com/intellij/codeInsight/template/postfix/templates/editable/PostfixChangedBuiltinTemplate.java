// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates.editable;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Represents the template that overrides the builtin one.
 * It's considered as builtin template: cannot be deleted via UI but can be restored to its initial state.
 */
public final class PostfixChangedBuiltinTemplate extends PostfixTemplateWrapper {
  private final @NotNull PostfixTemplate myBuiltinTemplate;

  public PostfixChangedBuiltinTemplate(@NotNull PostfixTemplate template, @NotNull PostfixTemplate builtin) {
    super(template);
    myBuiltinTemplate = builtin;
  }

  public @NotNull PostfixTemplate getBuiltinTemplate() {
    return myBuiltinTemplate;
  }

  @Override
  public boolean isBuiltin() {
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PostfixChangedBuiltinTemplate template)) return false;
    if (!super.equals(o)) return false;
    return Objects.equals(myBuiltinTemplate, template.myBuiltinTemplate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myBuiltinTemplate);
  }
}
