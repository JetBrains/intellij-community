// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.options.UnnamedConfigurable;

/**
 * To provide additional options in Editor | Code Completion section register implementation of
 * {@link UnnamedConfigurable} in the plugin.xml:
 * <p/>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;codeCompletionConfigurable instance="class-name"/&gt;<br>
 * &lt;/extensions&gt;
 * <p>
 * A new instance of the specified class will be created each time then the Settings dialog is opened
 *
 * If you need to add a section of code completion options, your UnnamedConfigurable should implement
 * {@link CodeCompletionOptionsCustomSection}
 */
public final class CodeCompletionConfigurableEP extends ConfigurableEP<UnnamedConfigurable> {
  static final ExtensionPointName<CodeCompletionConfigurableEP> EP_NAME = new ExtensionPointName<>("com.intellij.codeCompletionConfigurable");
}
