// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.backwardRefs.view;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class CompilerReferenceFindUsagesTestInfo {
  private final @Nullable Set<VirtualFile> myFiles;
  private final @NotNull DirtyScopeTestInfo myDirtyScopeInfo;

  public CompilerReferenceFindUsagesTestInfo(@Nullable Set<VirtualFile> occurrences, @NotNull DirtyScopeTestInfo dirtyScopeTestInfo) {
    myFiles = occurrences;
    myDirtyScopeInfo = dirtyScopeTestInfo;
  }

  private @NotNull List<VirtualFile> getFilesWithKnownOccurrences() {
    if (myFiles == null) {
      throw new IllegalStateException();
    }

    List<VirtualFile> list = new ArrayList<>();
    for (VirtualFile f : myFiles) {
      if (f != null && !myDirtyScopeInfo.getDirtyScope().contains(f)) {
        list.add(f);
      }
    }
    return list;
  }

  private Module @NotNull [] getDirtyModules() {
    return myDirtyScopeInfo.getDirtyModules();
  }

  private Module @NotNull [] getDirtyUnsavedModules() {
    return myDirtyScopeInfo.getDirtyUnsavedModules();
  }

  private VirtualFile @NotNull [] getExcludedFiles() {
    return myDirtyScopeInfo.getExcludedFiles();
  }

  private boolean isEnabled() {
    return myFiles != null;
  }

  DefaultMutableTreeNode asTree() {
    DefaultMutableTreeNode node = new DefaultMutableTreeNode();
    if (isEnabled()) {
      final DefaultMutableTreeNode knownOccurrences = new DefaultMutableTreeNode("Known occurrence files");
      node.add(knownOccurrences);
      for (VirtualFile file : getFilesWithKnownOccurrences()) {
        knownOccurrences.add(new DefaultMutableTreeNode(file));
      }

      final DefaultMutableTreeNode dirtyModules = new DefaultMutableTreeNode("Dirty modules");
      node.add(dirtyModules);
      for (Module module : getDirtyModules()) {
        dirtyModules.add(new DefaultMutableTreeNode(module));
      }

      final DefaultMutableTreeNode unsavedDirtyModules = new DefaultMutableTreeNode("Unsaved dirty modules");
      node.add(unsavedDirtyModules);
      for (Module module : getDirtyUnsavedModules()) {
        unsavedDirtyModules.add(new DefaultMutableTreeNode(module));
      }

      final DefaultMutableTreeNode excludedFiles = new DefaultMutableTreeNode("Current excluded files");
      node.add(excludedFiles);
      for (VirtualFile excludedFile : getExcludedFiles()) {
        excludedFiles.add(new DefaultMutableTreeNode(excludedFile));
      }
    }
    else {
      node.add(new DefaultMutableTreeNode("Service is not available"));
    }

    return node;
  }
}
