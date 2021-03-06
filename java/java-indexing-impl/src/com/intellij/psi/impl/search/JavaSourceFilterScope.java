// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

public class JavaSourceFilterScope extends DelegatingGlobalSearchScope {
  private final @Nullable ProjectFileIndex myIndex;
  private final boolean myIncludeVersions;
  private final boolean myIncludeLibrarySources;

  public JavaSourceFilterScope(@NotNull GlobalSearchScope delegate) {
    this(delegate, false);
  }

  public JavaSourceFilterScope(@NotNull GlobalSearchScope delegate, boolean includeVersions) {
    this(delegate, includeVersions, false);
  }

  /**
   * By default, the scope excludes version-specific classes of multi-release .jar files
   * (i.e. *.class files located under META-INF/versions/ directory).
   * Setting {@code includeVersions} parameter to {@code true} allows such files to pass the filter.
   */
  public JavaSourceFilterScope(@NotNull GlobalSearchScope delegate, boolean includeVersions, boolean includeLibrarySources) {
    super(delegate);
    Project project = getProject();
    myIndex = project == null ? null : ProjectRootManager.getInstance(project).getFileIndex();
    myIncludeVersions = includeVersions;
    myIncludeLibrarySources = includeLibrarySources;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    if (!super.contains(file)) {
      return false;
    }

    if (myIndex == null) {
      return false;
    }

    if (FileTypeRegistry.getInstance().isFileOfType(file, JavaClassFileType.INSTANCE)) {
      return myIndex.isInLibraryClasses(file) && (myIncludeVersions || !isVersioned(file, myIndex));
    }

    return myIndex.isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.SOURCES) ||
           (myIncludeLibrarySources || myBaseScope.isForceSearchingInLibrarySources()) && myIndex.isInLibrarySource(file);
  }

  private static boolean isVersioned(VirtualFile file, ProjectFileIndex index) {
    VirtualFile root = index.getClassRootForFile(file);
    while ((file = file.getParent()) != null && !file.equals(root)) {
      if (Comparing.equal(file.getNameSequence(), "versions")) {
        VirtualFile parent = file.getParent();
        if (parent != null && Comparing.equal(parent.getNameSequence(), "META-INF")) {
          return true;
        }
      }
    }

    return false;
  }
}