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
package com.intellij.ide.scriptingContext;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Rustam Vishnyakov
 */
public abstract class LangScriptingContextProvider {

  @NotNull
  public abstract Language getLanguage();

  public abstract LibraryType getLibraryType();

  public abstract boolean acceptsExtension(String fileExt);

  public abstract ScriptingLibraryMappings getLibraryMappings(Project project);

  public abstract boolean isCompact(VirtualFile file);

  @Nullable
  public abstract String getLibraryTypeName(OrderRootType rootType);
  
  @Nullable
  public abstract String getDefaultDocUrl(VirtualFile file);
  
  @Nullable
  public abstract String getOfflineDocUrl(String defaultDocUrl);
  
  @Nullable
  public abstract VirtualFile downloadOfflineDoc(Project project, String offlineDocUrl, JComponent parent);
  
  public abstract boolean isPredefinedLibrary(Project project, String libName);

}
