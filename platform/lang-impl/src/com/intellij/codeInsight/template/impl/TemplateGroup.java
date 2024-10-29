// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.openapi.options.CompoundScheme;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TemplateGroup extends CompoundScheme<TemplateImpl> {
  private final String myReplace;

  private boolean isModified = true;

  public boolean isModified() {
    return isModified;
  }

  public void setModified(boolean modified) {
    isModified = modified;
  }

  public TemplateGroup(final @NlsSafe String name) {
    this(name, null);
  }

  public TemplateGroup(@NlsSafe String name, @NlsSafe @Nullable String replace) {
    super(name);
    myReplace = replace;
  }

  public @NlsSafe String getReplace() {
    return myReplace;
  }

  public boolean containsTemplate(final @NotNull @NlsSafe String key, final @Nullable @NonNls String id) {
    return ContainerUtil.or(getElements(), template -> key.equals(template.getKey()) || id != null && id.equals(template.getId()));
  }

  @Override
  public String toString() {
    return getName();
  }
}
