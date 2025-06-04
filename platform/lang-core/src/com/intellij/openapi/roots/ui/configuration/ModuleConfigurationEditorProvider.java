// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.ModuleConfigurationEditor;
import org.jetbrains.annotations.NotNull;

/**
 * Implement this interface to provide tabs for a module editor in 'Project Structure' dialog. The implementation should be registered in your {@code plugin.xml}:
 * <pre>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;
 * &nbsp;&nbsp;&lt;moduleConfigurationEditorProvider implementation="qualified-class-name"/&gt;
 * &lt;/extensions&gt;
 * </pre>
 */
public interface ModuleConfigurationEditorProvider {
  @NotNull ModuleConfigurationEditor @NotNull [] createEditors(@NotNull ModuleConfigurationState state);
}
