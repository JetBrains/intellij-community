// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates.impl;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public final class BundledFileTemplate extends FileTemplateBase {
  private final DefaultTemplate myDefaultTemplate;
  private final boolean myInternal;
  private boolean myEnabled = true; // when user 'deletes' bundled plugin, it simply becomes disabled

  public BundledFileTemplate(@NotNull DefaultTemplate defaultTemplate, boolean internal) {
    myDefaultTemplate = defaultTemplate;
    myInternal = internal;
  }

  // these complications are to avoid eager initialization/load of huge files
  @Override
  public boolean isLiveTemplateEnabled() {
    if (isLiveTemplateEnabledChanged()) {
      return super.isLiveTemplateEnabled();
    }
    return isLiveTemplateEnabledByDefault();
  }

  @Override
  @NotNull
  public String getName() {
    return myDefaultTemplate.getName();
  }

  @Override
  @NotNull
  public String getExtension() {
    return myDefaultTemplate.getExtension();
  }

  @Override
  public void setName(@NotNull String name) {
    // empty, cannot change name for bundled template
  }

  @Override
  public void setExtension(@NotNull String extension) {
    // empty, cannot change extension for bundled template
  }

  @Override
  @NotNull
  protected String getDefaultText() {
    return myDefaultTemplate.getText();
  }

  @Override
  @NotNull
  public String getDescription() {
    return myDefaultTemplate.getDescriptionText();
  }

  @Override
  public boolean isDefault() {
    // todo: consider isReformat option here?
    return getText().equals(getDefaultText());
  }

  @NotNull
  @Override
  public BundledFileTemplate clone() {
    return (BundledFileTemplate)super.clone();
  }

  boolean isEnabled() {
    return myInternal || myEnabled;
  }

  void setEnabled(boolean enabled) {
    if (enabled != myEnabled) {
      myEnabled = enabled;
      if (!enabled) {
        revertToDefaults();
      }
    }
  }

  void revertToDefaults() {
    setText(null);
    setReformatCode(DEFAULT_REFORMAT_CODE_VALUE);
    setLiveTemplateEnabled(isLiveTemplateEnabledByDefault());
  }

  boolean isTextModified() {
    return !getText().equals(getDefaultText());
  }

  @Override
  public boolean isLiveTemplateEnabledByDefault() {
    return myDefaultTemplate.getText().contains("#[[$");
  }

  @Override
  public String toString() {
    return myDefaultTemplate.toString();
  }
}
