/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

  public String getTooltip() {
    String hash = DvcsUtil.getShortHash(getUserObject().getId().toString());
    String date = DvcsUtil.getDateString(getUserObject());
    String author = VcsUserUtil.getShortPresentation(getUserObject().getAuthor());
    String message = IssueLinkHtmlRenderer.formatTextWithLinks(myProject, getUserObject().getFullMessage());
    return String.format("%s  %s  by %s\n\n%s", hash, date, author, message);
  }
}
