// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.backwardRefs.view;

import com.intellij.compiler.CompilerDirectHierarchyInfo;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.stream.Stream;

public class CompilerReferenceHierarchyTestInfo {
  private final @Nullable CompilerDirectHierarchyInfo myHierarchyInfo;
  private final @NotNull DirtyScopeTestInfo myDirtyScopeInfo;

  public CompilerReferenceHierarchyTestInfo(@Nullable CompilerDirectHierarchyInfo hierarchyInfo,
                                            @NotNull DirtyScopeTestInfo dirtyScopeTestInfo) {
    myHierarchyInfo = hierarchyInfo;
    myDirtyScopeInfo = dirtyScopeTestInfo;
  }

  public @NotNull Stream<PsiElement> getHierarchyChildren() {
    if (myHierarchyInfo == null) throw new IllegalArgumentException();
    return myHierarchyInfo.getHierarchyChildren();
  }

  Module @NotNull [] getDirtyModules() {
    return myDirtyScopeInfo.getDirtyModules();
  }

  Module @NotNull [] getDirtyUnsavedModules() {
    return myDirtyScopeInfo.getDirtyUnsavedModules();
  }

  VirtualFile @NotNull [] getExcludedFiles() {
    return myDirtyScopeInfo.getExcludedFiles();
  }

  boolean isEnabled() {
    return myHierarchyInfo != null;
  }

  DefaultMutableTreeNode asTree() {
    DefaultMutableTreeNode node = new DefaultMutableTreeNode();
    if (isEnabled()) {
      final DefaultMutableTreeNode knownOccurrences = new DefaultMutableTreeNode("Known hierarchy direct children");
      node.add(knownOccurrences);
      getHierarchyChildren().forEach(e -> knownOccurrences.add(new DefaultMutableTreeNode(e)));

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
