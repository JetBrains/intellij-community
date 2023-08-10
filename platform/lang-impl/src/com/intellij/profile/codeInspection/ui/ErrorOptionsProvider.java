// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui;

import com.intellij.openapi.options.UnnamedConfigurable;

/**
 * Register an implementation of this interface in {@code plugin.xml}
 * to provide additional options in the "Editor | Code Editing | Error highlighting" section:
 * <p>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;errorOptionsProvider instance="class-name"/&gt;<br>
 * &lt;/extensions&gt;
 * <p>
 * A new instance of the specified class will be created each time the Settings dialog is opened.
 */
public interface ErrorOptionsProvider extends UnnamedConfigurable {
}