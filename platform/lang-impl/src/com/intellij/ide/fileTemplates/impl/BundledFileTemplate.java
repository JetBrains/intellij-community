// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates.impl;

import com.intellij.ide.fileTemplates.PluginBundledTemplate;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public final class BundledFileTemplate extends FileTemplateBase implements PluginBundledTemplate {
  private final DefaultTemplate myDefaultTemplate;
  private final boolean myInternal;
  private boolean myEnabled = true; // when user 'deletes' bundled plugin, it simply becomes disabled

  public BundledFileTemplate(@NotNull DefaultTemplate defaultTemplate, boolean internal) {
    myDefaultTemplate = defaultTemplate;
    myInternal = internal;
  }

  @Override
  public @NotNull PluginDescriptor getPluginDescriptor() {
    return myDefaultTemplate.pluginDescriptor;
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
  public @NotNull String getName() {
    return myDefaultTemplate.getName();
  }

  @Override
  public @NotNull String getExtension() {
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
  protected @NotNull String getDefaultText() {
    return myDefaultTemplate.getText();
  }

  @Override
  public @NotNull String getDescription() {
    return myDefaultTemplate.getDescriptionText();
  }

  @Override
  public boolean isDefault() {
    // todo: consider isReformat option here?
    return getText().equals(getDefaultText());
  }

  @Override
  public @NotNull BundledFileTemplate clone() {
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
