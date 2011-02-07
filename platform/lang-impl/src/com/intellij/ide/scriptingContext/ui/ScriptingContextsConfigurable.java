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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.scriptingContext.LangScriptingContextConfigurable;
import com.intellij.ide.scriptingContext.ScriptingLibraryMappings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.scripting.ScriptingLibraryTable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.tree.LanguagePerFileConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;

/**
 * @author Rustam Vishnyakov
 */
public class ScriptingContextsConfigurable extends LanguagePerFileConfigurable<ScriptingLibraryTable.LibraryModel>
  implements LibraryTable.Listener,
             Disposable {

  private final ScriptingLibraryMappings myScriptingLibraryMappings;
  private final LangScriptingContextConfigurable myParent;

  public ScriptingContextsConfigurable(final LangScriptingContextConfigurable parent, final Project project, final ScriptingLibraryMappings mappings) {
    super(project, ScriptingLibraryTable.LibraryModel.class, mappings,
          IdeBundle.message("scripting.lib.usageScope.caption"),
          IdeBundle.message("scripting.lib.usageScope.tableTitle"),
          IdeBundle.message("scripting.lib.usageScope.override.question"),
          IdeBundle.message("scripting.lib.usageScope.override.title"));
    myScriptingLibraryMappings = mappings;
    myParent = parent;
    mappings.registerLibraryTableListener(this, this);
    Disposer.register(project, this);
  }

  public void resetMappings() {
    myScriptingLibraryMappings.reset();
  }

  @Override
  public void reset() {
    resetMappings();
    if (getTreeView() != null) {
      super.reset();
    }
  }

  @Override
  protected Icon getIcon(ScriptingLibraryTable.LibraryModel currValue, ScriptingLibraryTable.LibraryModel libraryModel) {
    if (libraryModel == null) return ScriptingLibraryIcons.CLEAR_ICON;
    if (currValue instanceof ScriptingLibraryMappings.CompoundLibrary) {
      if (((ScriptingLibraryMappings.CompoundLibrary)currValue).containsLibrary(libraryModel.getName())) {
        return ScriptingLibraryIcons.CHECKED_ICON;
      }
    }
    return ScriptingLibraryIcons.UNCHECKED_ICON;
  }

  @Override
  protected String visualize(@NotNull ScriptingLibraryTable.LibraryModel library) {
    return library.getName();
  }

  @Nls
  @Override
  public String getDisplayName() {
    return IdeBundle.message("scripting.lib.usageScope");
  }

  @Override
  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableFileTypes.png");
  }

  @Override
  public String getHelpTopic() {
    return myParent.getUsageScopeHelpTopic();
  }

  @Override
  public void apply() throws ConfigurationException {
    super.apply();
    Map<VirtualFile,ScriptingLibraryTable.LibraryModel> libMappings = myScriptingLibraryMappings.getMappings();
    for (ScriptingLibraryTable.LibraryModel libraryModel : libMappings.values()) {
      if (libraryModel instanceof ScriptingLibraryMappings.CompoundLibrary) {
        ((ScriptingLibraryMappings.CompoundLibrary)libraryModel).applyChanges();
      }
    }
  }

  @Override
  public boolean isModified() {
    if (super.isModified()) return true;
    Map<VirtualFile,ScriptingLibraryTable.LibraryModel> libMappings = myScriptingLibraryMappings.getMappings();
    for (ScriptingLibraryTable.LibraryModel libraryModel : libMappings.values()) {
      if (libraryModel instanceof ScriptingLibraryMappings.CompoundLibrary) {
        if (((ScriptingLibraryMappings.CompoundLibrary)libraryModel).isModified()) return true;
      }
    }
    return false;
  }

  @Override
  public void dispose() {
  }

  @Override
  public void afterLibraryAdded(Library newLibrary) {
    reset();
  }

  @Override
  public void afterLibraryRenamed(Library library) {
    reset();
  }

  @Override
  public void beforeLibraryRemoved(Library library) {
  }

  @Override
  public void afterLibraryRemoved(Library library) {
    reset();
  }
}
