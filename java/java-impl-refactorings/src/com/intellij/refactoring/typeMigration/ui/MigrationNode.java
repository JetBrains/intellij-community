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

import java.util.*;

/**
 * @author anna
 */
public class MigrationNode extends AbstractTreeNode<TypeMigrationUsageInfo> implements DuplicateNodeRenderer.DuplicatableNode<MigrationNode> {
  private final TypeMigrationUsageInfo myInfo;
  private MigrationNode myDuplicatedNode;
  private List<MigrationNode> myCachedChildren;
  private final TypeMigrationLabeler myLabeler;
  private final PsiType myMigrationType;
  private final HashMap<TypeMigrationUsageInfo, Set<MigrationNode>> myProcessed;
  private final HashSet<? extends TypeMigrationUsageInfo> myParents;

  public MigrationNode(final Project project,
                       final TypeMigrationUsageInfo info,
                       final PsiType migrationType,
                       final TypeMigrationLabeler labeler,
                       final HashSet<? extends TypeMigrationUsageInfo> parents,
                       final HashMap<TypeMigrationUsageInfo, Set<MigrationNode>> processed) {
    super(project, info);
    myLabeler = labeler;
    myMigrationType = migrationType;
    myProcessed = processed;
    myParents = parents;

    Set<MigrationNode> alreadyAdded = myProcessed.get(info);
    if (alreadyAdded == null) {
      alreadyAdded = new HashSet<>();
      myProcessed.put(info, alreadyAdded);
      myInfo = info;
    }
    else {
      final MigrationNode duplicate = alreadyAdded.iterator().next();
      myInfo = duplicate.getInfo();
      myDuplicatedNode = duplicate;
    }
    alreadyAdded.add(this);
  }

  public TypeMigrationUsageInfo getInfo() {
    return myInfo;
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode<?>> getChildren() {
    if (myCachedChildren == null) {
      myCachedChildren = new ArrayList<>();

      final PsiElement element = myInfo.getElement();
      if (element != null) {
        try {
          myLabeler.setRootAndMigrate(myInfo, myMigrationType, myLabeler.markRootUsages(element, myMigrationType));
        }
        catch (TypeMigrationLabeler.MigrateException e) {
          //skip warning
        }

        final HashSet<Pair<TypeMigrationUsageInfo, PsiType>> roots = myLabeler.getRootsTree().get(myInfo);
        if (roots != null) {
          for (Pair<TypeMigrationUsageInfo, PsiType> root : roots) {

            final TypeMigrationUsageInfo info = root.getFirst();

            if (myParents.contains(info)) continue;
            final HashSet<TypeMigrationUsageInfo> parents = new HashSet<>(myParents);
            parents.add(info);

            final MigrationNode migrationNode =
                new MigrationNode(getProject(), info, root.getSecond(), myLabeler, parents, myProcessed);

            if (myInfo.isExcluded()) {
              info.setExcluded(true);
            }

            myCachedChildren.add(migrationNode);
          }
        }
      }
    }
    return myCachedChildren;
  }

  public boolean areChildrenInitialized() {
    return myCachedChildren != null;
  }

  @Override
  protected void update(@NotNull final PresentationData presentation) {

  }

  @Override
  public MigrationNode getDuplicate() {
    return myDuplicatedNode;
  }
}
