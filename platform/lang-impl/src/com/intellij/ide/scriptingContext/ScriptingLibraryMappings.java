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

import com.intellij.lang.LanguagePerFileMappings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.libraries.scripting.ScriptingLibraryManager;
import com.intellij.openapi.roots.libraries.scripting.ScriptingLibraryTable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Rustam Vishnyakov
 */
public class ScriptingLibraryMappings extends LanguagePerFileMappings<ScriptingLibraryTable.LibraryModel> {

  private final ScriptingLibraryManager myLibraryManager;

  public ScriptingLibraryMappings(final Project project, final LibraryType libraryType) {
    super(project);
    myLibraryManager = new ScriptingLibraryManager(project, libraryType);
  }

  protected String serialize(final ScriptingLibraryTable.LibraryModel library) {
    return library.getName();
  }

  public List<ScriptingLibraryTable.LibraryModel> getAvailableValues() {
    return getLibraries();
  }

  @Override
  protected ScriptingLibraryTable.LibraryModel getDefaultMapping(@Nullable VirtualFile file) {
    return null;
  }

  public List<ScriptingLibraryTable.LibraryModel> getLibraries() {
    ArrayList<ScriptingLibraryTable.LibraryModel> libraryModels = new ArrayList<ScriptingLibraryTable.LibraryModel>();
    libraryModels.addAll(Arrays.asList(myLibraryManager.getLibraries()));
    return libraryModels;
  }

}
