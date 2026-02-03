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
 * 
 * The implementation must have a no-arg constructor. The {@link com.intellij.openapi.module.Module} instance can be taken from 
 * {@code state} parameter of {@link #createEditors} ({@code state.getCurrentRootModel().getModule()}).
 * <br>
 * Please note that although this interface is located in the platform, the extension point is used in Java plugin where 'Project Structure' 
 * dialog is available. So the implementations will be ignored if the Java plugin isn't installed in the IDE.  
 */
public interface ModuleConfigurationEditorProvider {
  @NotNull ModuleConfigurationEditor @NotNull [] createEditors(@NotNull ModuleConfigurationState state);
}
