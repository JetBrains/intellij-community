// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates.impl;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public final class CustomFileTemplate extends FileTemplateBase {
  private String myName;
  private String myExtension;

  public CustomFileTemplate(@NotNull String name, @NotNull String extension) {
    myName = name;
    myExtension = extension;
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public void setName(@NotNull String name) {
    myName = name;
    updateChildrenNames();
  }

  @Override
  public @NotNull String getExtension() {
    return myExtension;
  }

  @Override
  public void setExtension(@NotNull String extension) {
    myExtension = extension;
    updateChildrenNames();
  }

  @Override
  public @NotNull String getDescription() {
    return "";  // todo: some default description?
  }

  @Override
  public @NotNull CustomFileTemplate clone() {
    return (CustomFileTemplate)super.clone();
  }

  @Override
  public boolean isDefault() {
    return false;
  }
}
