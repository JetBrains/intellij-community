/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.application.options.editor;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.project.Project;

/**
 * Register implementation of {@link AutoImportOptionsProvider} in the plugin.xml to provide additional options in Editor | Auto Import section:
 * <p/>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;autoImportOptionsProvider instance="class-name"/&gt;<br>
 * &lt;/extensions&gt;
 * <p>
 * A new instance of the specified class will be created each time then the Settings dialog is opened
 *
 * @author nik
 */
public class AutoImportOptionsProviderEP extends ConfigurableEP<AutoImportOptionsProvider> {
  public static final ExtensionPointName<AutoImportOptionsProviderEP> EP_NAME = ExtensionPointName.create("com.intellij.autoImportOptionsProvider");

  public AutoImportOptionsProviderEP(Project project) {
    super(project);
  }
}
