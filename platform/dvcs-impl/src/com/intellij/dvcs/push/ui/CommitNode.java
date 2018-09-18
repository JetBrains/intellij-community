// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.push.ui;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.util.VcsUserUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;

public class CommitNode extends DefaultMutableTreeNode implements CustomRenderedTreeNode, TooltipNode {

  @NotNull private final Project myProject;

  public CommitNode(@NotNull Project project, @NotNull VcsFullCommitDetails commit) {
    super(commit, false);
    myProject = project;
  }

  @Override
  public VcsFullCommitDetails getUserObject() {
    return (VcsFullCommitDetails)super.getUserObject();
  }

  @Override
  public void render(@NotNull ColoredTreeCellRenderer renderer) {
    renderer.append("   ");
    TreeNode parent = getParent();
    new IssueLinkRenderer(myProject, renderer).appendTextWithLinks(getUserObject().getSubject(), PushLogTreeUtil
      .addTransparencyIfNeeded(renderer, SimpleTextAttributes.REGULAR_ATTRIBUTES,
                               !(parent instanceof RepositoryNode) || ((RepositoryNode)parent).isChecked()));
  }

  @Override
  public String getTooltip() {
    String hash = DvcsUtil.getShortHash(getUserObject().getId().toString());
    String date = DvcsUtil.getDateString(getUserObject());
    String author = VcsUserUtil.getShortPresentation(getUserObject().getAuthor());
    String message = IssueLinkHtmlRenderer.formatTextWithLinks(myProject, getUserObject().getFullMessage());
    return String.format("%s  %s  by %s\n\n%s", hash, date, author, message);
  }
}
