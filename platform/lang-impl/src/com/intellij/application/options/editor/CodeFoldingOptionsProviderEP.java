// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.ConfigurableEP;

/**
 * Register implementation of {@link CodeFoldingOptionsProvider} in the plugin.xml to provide additional options in Editor | Code Folding section:
 * <p/>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;codeFoldingOptionsProvider instance="class-name"/&gt;<br>
 * &lt;/extensions&gt;
 * <p>
 * A new instance of the specified class will be created each time then the Settings dialog is opened

 */
public final class CodeFoldingOptionsProviderEP extends ConfigurableEP<CodeFoldingOptionsProvider> {
  public static final ExtensionPointName<CodeFoldingOptionsProviderEP> EP_NAME = ExtensionPointName.create("com.intellij.codeFoldingOptionsProvider");
}
