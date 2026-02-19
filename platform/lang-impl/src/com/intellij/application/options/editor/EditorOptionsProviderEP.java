// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.ConfigurableEP;

/**
 * Register implementation of {@link EditorOptionsProvider} in the plugin.xml to provide sub-section of Editor section in the Settings dialog:
 * <p/>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;editorOptionsProvider instance="class-name"/&gt;<br>
 * &lt;/extensions&gt;
 * <p>
 * A new instance of the specified class will be created each time then the Settings dialog is opened
 */
public final class EditorOptionsProviderEP extends ConfigurableEP<EditorOptionsProvider> {
  public static final ExtensionPointName<EditorOptionsProviderEP> EP_NAME = new ExtensionPointName<>("com.intellij.editorOptionsProvider");
}
