/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.util.containers.HashSet;
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

  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
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

  protected void update(final PresentationData presentation) {

  }

  public DefaultMutableTreeNode getDuplicate() {
    return null;
  }

}
