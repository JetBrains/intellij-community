// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.profile.codeInspection.ui;

import com.intellij.openapi.options.UnnamedConfigurable;

/**
 * Register implementation of this interface in the plugin.xml to provide additional options in "Editor | Code Editing | Error highlighting" section:
 * <p/>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;errorOptionsProvider instance="class-name"/&gt;<br>
 * &lt;/extensions&gt;
 * <p>
 * A new instance of the specified class will be created each time then the Settings dialog is opened.
 */
public interface ErrorOptionsProvider extends UnnamedConfigurable {
}