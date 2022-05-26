// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.typeMigration.ui;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.ui.DuplicateNodeRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.*;

/**
 * @author anna
 */
public class MigrationRootNode extends AbstractTreeNode<TypeMigrationLabeler> implements DuplicateNodeRenderer.DuplicatableNode  {
  private final TypeMigrationLabeler myLabeler;
  private List<MigrationNode> myCachedChildren;
  private final PsiElement[] myRoots;
  private final boolean myPreviewUsages;

  protected MigrationRootNode(Project project,
                              TypeMigrationLabeler labeler,
                              final PsiElement[] roots,
                              final boolean previewUsages) {
    super(project, labeler);
    myLabeler = labeler;
    myRoots = roots;
    myPreviewUsages = previewUsages;
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode<?>> getChildren() {
    if (myCachedChildren == null) {
      myCachedChildren = new ArrayList<>();
      if (myPreviewUsages) {
        for (Pair<TypeMigrationUsageInfo, PsiType> root : myLabeler.getMigrationRoots()) {
          addRoot(root.getFirst(), root.getSecond());
        }
      }
      else {
        for (PsiElement root : myRoots) {
          addRoot(new TypeMigrationUsageInfo(root), myLabeler.getMigrationRootTypeFunction().fun(root));
        }
      }
    }
    return myCachedChildren;
  }

  private void addRoot(TypeMigrationUsageInfo info, PsiType migrationType) {
    final HashSet<TypeMigrationUsageInfo> parents = new HashSet<>();
    parents.add(info);
    final MigrationNode migrationNode =
        new MigrationNode(getProject(), info, migrationType, myLabeler, parents, new HashMap<>());

    myCachedChildren.add(migrationNode);
  }

  @Override
  protected void update(@NotNull final PresentationData presentation) {

  }

  @Override
  public DefaultMutableTreeNode getDuplicate() {
    return null;
  }

}
