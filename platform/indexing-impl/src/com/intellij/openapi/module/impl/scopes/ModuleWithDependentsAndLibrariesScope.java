// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.roots.impl.DirectoryInfo;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Simple override of {@link ModuleWithDependentsScope} adding Libraries into the scope
 * Created by brian.mcnamara on Jul 26 2018
 **/
public class ModuleWithDependentsAndLibrariesScope extends ModuleWithDependentsScope {

  ModuleWithDependentsAndLibrariesScope(@NotNull Module module) {
    super(module);
  }

  @Override
  boolean contains(@NotNull VirtualFile file, boolean myOnlyTests) {
    //TODO: does this work for library classes?
    if (myOnlyTests && !TestSourcesFilter.isTestSources(file, getProject())) return false;
    DirectoryInfo info = myProjectFileIndex.getInfoForFileOrDirectory(file);
    if (info.isInProject(file) && info.hasLibraryClassRoot()) return true;
    return super.contains(info, file, myOnlyTests);
  }
}
