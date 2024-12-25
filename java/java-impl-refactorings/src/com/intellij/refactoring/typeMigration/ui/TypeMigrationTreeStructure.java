// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.ui;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.util.treeView.AbstractTreeStructureBase;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author anna
 */
public class TypeMigrationTreeStructure extends AbstractTreeStructureBase {
  private MigrationRootNode myRoot;

  public TypeMigrationTreeStructure(final Project project) {
    super(project);
  }

  public void setRoots(final MigrationRootNode root) {
    myRoot = root;
  }

  @Override
  public List<TreeStructureProvider> getProviders() {
    return Collections.emptyList();
  }

  @Override
  public @NotNull Object getRootElement() {
    return myRoot;
  }

  @Override
  public void commit() {

  }

  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }

  @Override
  public boolean isToBuildChildrenInBackground(final @NotNull Object element) {
    return true;
  }
}