// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.UnnamedConfigurable;

/**
 * To provide additional options in Editor | Editor Tabs section register implementation of
 * {@link UnnamedConfigurable} in the plugin.xml:
 * <p/>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;editorTabsConfigurable instance="class-name"/&gt;<br>
 * &lt;/extensions&gt;
 * <p>
 * A new instance of the specified class will be created each time then the Settings dialog is opened
 *
 * If you need to add a section of editor tabs options, your UnnamedConfigurable should implement
 * {@link EditorTabsOptionsCustomSection}
 */
public final class EditorTabsConfigurableEP extends ConfigurableEP<SearchableConfigurable> {
  static final ExtensionPointName<EditorTabsConfigurableEP> EP_NAME = new ExtensionPointName<>("com.intellij.editorTabsConfigurable");
}
