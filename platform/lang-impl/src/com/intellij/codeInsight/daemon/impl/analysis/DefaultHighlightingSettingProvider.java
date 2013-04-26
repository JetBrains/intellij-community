/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ksafonov
 */


/**
 * Implementation can provide default level of highlighting (one of "none", "syntax checks", "inspections") for a file.
 * This level can be overridden by user for a file via Hector-the-inspector component.
 * If implementation returns <code>null</code>, next one is checked. If nobody returns anything, "Inspections" level will be used
 * Implement {@link com.intellij.openapi.project.DumbAware} interface to allow implementation to be called in dumb mode
 */
public abstract class DefaultHighlightingSettingProvider {
  public static final ExtensionPointName<DefaultHighlightingSettingProvider> EP_NAME = ExtensionPointName.create("com.intellij.defaultHighlightingSettingProvider");

  @Nullable
  public abstract FileHighlightingSetting getDefaultSetting(@NotNull Project project, @NotNull VirtualFile file);
}
