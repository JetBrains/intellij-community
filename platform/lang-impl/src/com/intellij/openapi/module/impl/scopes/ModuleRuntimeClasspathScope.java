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
package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * @author max
 */
public class ModuleRuntimeClasspathScope extends GlobalSearchScope {
  private final ProjectFileIndex myIndex;
  private final boolean myIncludeTests;
  private final LinkedHashSet<VirtualFile> myEntries = new LinkedHashSet<VirtualFile>();
  private final Module myModule;

  public ModuleRuntimeClasspathScope(final Module module, boolean includeTests) {
    super(module.getProject());
    myModule = module;
    myIndex = ProjectRootManager.getInstance(module.getProject()).getFileIndex();
    myIncludeTests = includeTests;
    buildEntries(module);
  }

  public int hashCode() {
    return myModule.hashCode();
  }

  public boolean equals(Object object) {
    if (object == this) return true;
    if (object.getClass() != ModuleRuntimeClasspathScope.class) return false;

    final ModuleRuntimeClasspathScope that = ((ModuleRuntimeClasspathScope)object);
    return that.myModule == myModule && that.myIncludeTests == myIncludeTests;
  }

  private void buildEntries(final Module module) {
    ModuleRootManager.getInstance(module).orderEntries().recursively().process(new RootPolicy<LinkedHashSet<VirtualFile>>() {
      private boolean myJDKProcessed = false;

      public LinkedHashSet<VirtualFile> visitModuleSourceOrderEntry(final ModuleSourceOrderEntry moduleSourceOrderEntry,
                                                                    final LinkedHashSet<VirtualFile> value) {
        value.addAll(Arrays.asList(moduleSourceOrderEntry.getFiles(OrderRootType.SOURCES)));
        return value;
      }

      public LinkedHashSet<VirtualFile> visitLibraryOrderEntry(final LibraryOrderEntry libraryOrderEntry,
                                                               final LinkedHashSet<VirtualFile> value) {
        value.addAll(Arrays.asList(libraryOrderEntry.getFiles(OrderRootType.CLASSES)));
        return value;
      }

      public LinkedHashSet<VirtualFile> visitJdkOrderEntry(final JdkOrderEntry jdkOrderEntry, final LinkedHashSet<VirtualFile> value) {
        if (myJDKProcessed) return value;
        myJDKProcessed = true;
        value.addAll(Arrays.asList(jdkOrderEntry.getFiles(OrderRootType.CLASSES)));
        return value;
      }
    }, myEntries);


  }

  public boolean contains(VirtualFile file) {
    if (!myIncludeTests && myIndex.isInTestSourceContent(file)) return false;
    return myEntries.contains(getFileRoot(file));
  }

  private VirtualFile getFileRoot(VirtualFile file) {
    if (myIndex.isLibraryClassFile(file)) {
      return myIndex.getClassRootForFile(file);
    }
    if (myIndex.isInContent(file)) {
      return myIndex.getSourceRootForFile(file);
    }
    return null;
  }

  public int compare(VirtualFile file1, VirtualFile file2) {
    final VirtualFile r1 = getFileRoot(file1);
    final VirtualFile r2 = getFileRoot(file2);
    for (VirtualFile root : myEntries) {
      if (r1 == root) return 1;
      if (r2 == root) return -1;
    }
    return 0;
  }

  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return true;
  }

  public boolean isSearchInLibraries() {
    return true;
  }

  public String getDisplayName() {
    return PsiBundle.message("runtime.scope.display.name", myModule.getName());
  }
}
