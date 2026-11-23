// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor;

import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.options.UnnamedConfigurable;

/**
 * To provide additional options in Editor | Smart Keys section register implementation of
 * {@link UnnamedConfigurable} in the plugin.xml:
 * <p/>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;editorSmartKeysConfigurable instance="class-name"/&gt;<br>
 * &lt;/extensions&gt;
 * <p>
 * A new instance of the specified class is created each time the Settings dialog opens.
 * A child page must declare a label in {@code plugin.xml} to stay lazy while the settings tree is built.
 */
public final class EditorSmartKeysConfigurableEP extends ConfigurableEP<UnnamedConfigurable> {
}
