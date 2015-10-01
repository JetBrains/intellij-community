/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * Implement this interface to provide tabs for a module editor in 'Project Structure' dialog. The implementation should be registered in your {@code plugin.xml}:
 * <pre>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;
 * &nbsp;&nbsp;&lt;moduleConfigurationEditorProvider implementation="qualified-class-name"/&gt;
 * &lt;/extensions&gt;
 * </pre>
 *
 * @author nik
 */
public interface ModuleConfigurationEditorProvider {
  ExtensionPointName<ModuleConfigurationEditorProvider> EP_NAME = ExtensionPointName.create("com.intellij.moduleConfigurationEditorProvider"); 

  ModuleConfigurationEditor[] createEditors(ModuleConfigurationState state);
}