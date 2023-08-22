// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.impl;

import com.intellij.compiler.ModuleSourceSet;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.ExportableUserDataHolderBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class OneProjectItemCompileScope extends ExportableUserDataHolderBase implements CompileScope{
  private static final Logger LOG = Logger.getInstance(OneProjectItemCompileScope.class);
  private final Project myProject;
  private final VirtualFile myFile;
  private final String myUrl;

  public OneProjectItemCompileScope(Project project, @NotNull VirtualFile file) {
    myProject = project;
    myFile = file;
    final String url = file.getUrl();
    myUrl = file.isDirectory()? url + "/" : url;
  }

  @Override
  public VirtualFile @NotNull [] getFiles(final FileType fileType, final boolean inSourceOnly) {
    final List<VirtualFile> files = new ArrayList<>(1);
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    final ContentIterator iterator = new CompilerContentIterator(fileType, projectFileIndex, inSourceOnly, files);
    if (myFile.isDirectory()){
      projectFileIndex.iterateContentUnderDirectory(myFile, iterator);
    }
    else{
      iterator.processFile(myFile);
    }
    return VfsUtilCore.toVirtualFileArray(files);
  }

  @Override
  public boolean belongs(@NotNull String url) {
    if (myFile.isDirectory()){
      return FileUtil.startsWith(url, myUrl);
    }
    return FileUtil.pathsEqual(url, myUrl);
  }

  @Override
  public Module @NotNull [] getAffectedModules() {
    final Collection<ModuleSourceSet> sets = getAffectedSourceSets();
    if (sets.isEmpty()) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Module is null for file " + myFile.getPresentableUrl());
      }
      return Module.EMPTY_ARRAY;
    }
    return new Module[] {sets.iterator().next().getModule()};
  }

  @Override
  public Collection<ModuleSourceSet> getAffectedSourceSets() {
    if (myProject.isDefault()) {
      return Collections.emptyList();
    }
    final @NotNull ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(myProject);
    final Module module = fileIndex.getModuleForFile(myFile);
    if (module == null || !fileIndex.isInSourceContent(myFile)) {
      return Collections.emptyList();
    }

    JpsModuleSourceRootType<?> rootType = fileIndex.getContainingSourceRootType(myFile);
    if (rootType == null) return Collections.emptyList();
    
    final boolean isResource = rootType instanceof JavaResourceRootType;
    final ModuleSourceSet.Type type = rootType.isForTests()?
      isResource? ModuleSourceSet.Type.RESOURCES_TEST :  ModuleSourceSet.Type.TEST :
      isResource? ModuleSourceSet.Type.RESOURCES :  ModuleSourceSet.Type.PRODUCTION;
    return Collections.singleton(new ModuleSourceSet(module, type));
  }
}
