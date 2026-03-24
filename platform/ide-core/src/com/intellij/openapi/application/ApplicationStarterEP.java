// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.serviceContainer.LazyExtensionInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ApplicationStarterEP extends LazyExtensionInstance<ApplicationStarter> implements PluginAware {
  /// The starter implementation class.
  @RequiredElement
  @Attribute("implementation")
  public String implementation;

  /// `true` if the starter implements an internal command that is not present in the "--list-commands" output by default.
  @Attribute("internal")
  public boolean isInternal = false;

  /// A resource bundle hosting the short description of the command for the "--list-commands" output.
  @Attribute("bundle")
  public String bundle;

  /// A resource key hosting the short description of the command for the "--list-commands" output.
  @Attribute("key")
  public String key;

  private PluginDescriptor myPluginDescriptor;

  @Override
  protected @Nullable String getImplementationClassName() {
    return implementation;
  }

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  public @NotNull ApplicationStarter get() {
    return getInstance(ApplicationManager.getApplication(), myPluginDescriptor);
  }
}
