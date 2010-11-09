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
package com.intellij.ide.scriptingContext.ui;

import com.intellij.ide.scriptingContext.LangScriptingContextProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.scripting.ScriptingLibraryTable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.tree.LanguagePerFileConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Rustam Vishnyakov
 */
public class ScriptingContextsConfigurable extends LanguagePerFileConfigurable<ScriptingLibraryTable.LibraryModel> {

  public ScriptingContextsConfigurable(final Project project, final LangScriptingContextProvider provider) {
    super(project, ScriptingLibraryTable.LibraryModel.class, provider.getLibraryMappings(project),
          "Specify which libraries are used in specific files and/or directories.", "Library",
          "Override library settings for child directories and files?", "Override Library Settings");
  }

  @Override
  protected String visualize(@NotNull ScriptingLibraryTable.LibraryModel library) {
    return library.getName();
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Usage Scope";
  }

  @Override
  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableFileTypes.png");
  }

  @Override
  public String getHelpTopic() {
    return null;
  }
}
