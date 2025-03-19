// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.ProjectExtension;
import com.intellij.openapi.roots.WatchedRootsProvider;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

final class CompilerProjectExtensionImpl extends CompilerProjectExtension implements Disposable {
  private static final String OUTPUT_TAG = "output";
  private static final String URL = "url";

  private VirtualFilePointer myCompilerOutput;
  private LocalFileSystem.WatchRequest myCompilerOutputWatchRequest;

  /**
   * Returns true if the compiler output was changed after read
   */
  private boolean readExternal(@NotNull Element element) {
    Element pathElement = element.getChild(OUTPUT_TAG);
    if (pathElement != null) {
      String outputPath = pathElement.getAttributeValue(URL);
      VirtualFilePointer oldValue = myCompilerOutput;
      myCompilerOutput = outputPath != null ? VirtualFilePointerManager.getInstance().create(outputPath, this, null) : null;

      return !Objects.equals(
        oldValue != null ? oldValue.getUrl() : null,
        myCompilerOutput != null ? myCompilerOutput.getUrl() : null
      );
    }
    return false;
  }

  private void writeExternal(@NotNull Element element) {
    if (myCompilerOutput != null) {
      Element pathElement = new Element(OUTPUT_TAG);
      pathElement.setAttribute(URL, myCompilerOutput.getUrl());
      element.addContent(pathElement);
    }
  }

  @Override
  public void dispose() {
    myCompilerOutput = null;
  }

  @Override
  public VirtualFile getCompilerOutput() {
    return myCompilerOutput != null ? myCompilerOutput.getFile() : null;
  }

  @Override
  public String getCompilerOutputUrl() {
    return myCompilerOutput != null ? myCompilerOutput.getUrl() : null;
  }

  @Override
  public VirtualFilePointer getCompilerOutputPointer() {
    return myCompilerOutput;
  }

  @Override
  public void setCompilerOutputPointer(VirtualFilePointer pointer) {
    myCompilerOutput = pointer;
  }

  @Override
  public void setCompilerOutputUrl(String compilerOutputUrl) {
    VirtualFilePointer pointer = VirtualFilePointerManager.getInstance().create(compilerOutputUrl, this, null);
    setCompilerOutputPointer(pointer);
    String path = VfsUtilCore.urlToPath(compilerOutputUrl);
    myCompilerOutputWatchRequest = LocalFileSystem.getInstance().replaceWatchedRoot(myCompilerOutputWatchRequest, path, true);
  }

  private static Set<String> getRootsToWatch(Project project) {
    Set<String> rootsToWatch = new HashSet<>();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
      if (extension != null && !extension.isCompilerOutputPathInherited()) {
        String outputUrl = extension.getCompilerOutputUrl();
        if (outputUrl != null && outputUrl.startsWith(StandardFileSystems.FILE_PROTOCOL_PREFIX)) {
          rootsToWatch.add(ProjectRootManagerImpl.Companion.extractLocalPath(outputUrl));
        }
        String testOutputUrl = extension.getCompilerOutputUrlForTests();
        if (testOutputUrl!= null && testOutputUrl.startsWith(StandardFileSystems.FILE_PROTOCOL_PREFIX)) {
          rootsToWatch.add(ProjectRootManagerImpl.Companion.extractLocalPath(testOutputUrl));
        }
      }
      // otherwise, the module output path is beneath the CompilerProjectExtension.getCompilerOutputUrl() which is added below
    }

    CompilerProjectExtension extension = CompilerProjectExtension.getInstance(project);
    if (extension != null) {
      String compilerOutputUrl = extension.getCompilerOutputUrl();
      if (compilerOutputUrl != null && compilerOutputUrl.startsWith(StandardFileSystems.FILE_PROTOCOL_PREFIX)) {
        rootsToWatch.add(ProjectRootManagerImpl.Companion.extractLocalPath(compilerOutputUrl));
      }
    }

    return rootsToWatch;
  }

  private static CompilerProjectExtensionImpl getImpl(Project project) {
    return (CompilerProjectExtensionImpl)CompilerProjectExtension.getInstance(project);
  }

  static final class MyProjectExtension extends ProjectExtension {
    private final Project myProject;

    MyProjectExtension(Project project) {
      myProject = project;
    }

    @Override
    public boolean readExternalElement(@NotNull Element element) {
      return getImpl(myProject).readExternal(element);
    }

    @Override
    public void writeExternal(@NotNull Element element) {
      getImpl(myProject).writeExternal(element);
    }
  }

  static final class MyWatchedRootsProvider implements WatchedRootsProvider {
    @Override
    public @NotNull Set<String> getRootsToWatch(@NotNull Project project) {
      return CompilerProjectExtensionImpl.getRootsToWatch(project);
    }
  }
}
