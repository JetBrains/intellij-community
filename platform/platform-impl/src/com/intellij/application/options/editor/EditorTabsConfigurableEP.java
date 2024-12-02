// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import org.jetbrains.annotations.ApiStatus;

/**
 * Provides additional options in <em>Editor | General | Editor Tabs</em> settings.
 * <p>
 * Register implementation of {@link UnnamedConfigurable} in {@code plugin.xml}:
 * <pre>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;
 *    &lt;editorTabsConfigurable instance="class-name"/&gt;
 * &lt;/extensions&gt;
 * </pre>
 * <p>
 * A new instance of the specified class will be created each time when the Settings dialog is opened.
 * <p>
 * If you need to add a section of editor tabs options, your {@code UnnamedConfigurable} should implement
 * {@link EditorTabsOptionsCustomSection}.
 */
public final class EditorTabsConfigurableEP extends ConfigurableEP<SearchableConfigurable> {
  static final ExtensionPointName<EditorTabsConfigurableEP> EP_NAME = new ExtensionPointName<>("com.intellij.editorTabsConfigurable");

  @ApiStatus.Internal
  public EditorTabsConfigurableEP() {
  }
}
