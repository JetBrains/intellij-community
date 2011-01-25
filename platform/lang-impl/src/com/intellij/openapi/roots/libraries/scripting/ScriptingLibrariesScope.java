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
package com.intellij.openapi.roots.libraries.scripting;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.IndexableSetContributor;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Rustam Vishnyakov
 */
public class ScriptingLibrariesScope extends GlobalSearchScope {

  private GlobalSearchScope myBaseScope;
  private ScriptingIndexableSetContributor myContributor;
  protected Set<VirtualFile> myLibraryFiles;

  public ScriptingLibrariesScope(GlobalSearchScope baseScope, Class<? extends IndexableSetContributor> providerClass) {
    super(baseScope.getProject());
    myBaseScope = baseScope;
    IndexableSetContributor contributor = IndexableSetContributor.EP_NAME.findExtension(providerClass);
    if (contributor instanceof  ScriptingIndexableSetContributor) {
      myContributor = (ScriptingIndexableSetContributor)contributor;
      setLibraryFiles();
    }
  }

  public void setLibraryFiles() {
    myLibraryFiles = myContributor.getLibraryFiles(myBaseScope.getProject());
  }

  public boolean contains(VirtualFile file) {
    return myBaseScope.contains(file) | (myLibraryFiles == null ? false : myLibraryFiles.contains(file));
  }

  @Override
  public boolean isSearchOutsideRootModel() {
    return true;
  }

  public int compare(VirtualFile file1, VirtualFile file2) {
    return myBaseScope.compare(file1, file2);
  }

  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return myBaseScope.isSearchInModuleContent(aModule);
  }

  public boolean isSearchInLibraries() {
    return myBaseScope.isSearchInLibraries();
  }
}
