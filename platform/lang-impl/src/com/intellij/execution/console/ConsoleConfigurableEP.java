// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.console;

import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.options.UnnamedConfigurable;

/**
 * Provides additional options in <em>Editor | General | Console</em> settings.
 * <p>
 * Register implementation of {@link UnnamedConfigurable} in {@code plugin.xml}:
 * <pre>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;
 *    &lt;consoleConfigurableProvider instance="class-name"/&gt;
 * &lt;/extensions&gt;
 * </pre>
 * <p>
 * A new instance of the specified class will be created each time when the Settings dialog is opened.
 */
public final class ConsoleConfigurableEP extends ConfigurableEP<UnnamedConfigurable> {
}
