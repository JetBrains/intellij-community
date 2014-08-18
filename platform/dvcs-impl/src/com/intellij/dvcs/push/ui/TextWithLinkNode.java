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


import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;

public class TextWithLinkNode extends DefaultMutableTreeNode implements CustomRenderedTreeNode {

  @NotNull protected VcsLinkedText myLinkedText;

  public TextWithLinkNode(@NotNull VcsLinkedText linkedText) {
    myLinkedText = linkedText;
  }

  public void fireOnClick(@NotNull TextWithLinkNode relatedNode) {
    TreeNode parent = relatedNode.getParent();
    if (parent instanceof RepositoryNode) {
      myLinkedText.hyperLinkActivate((RepositoryNode)parent);
    }
  }

  @Override
  public void render(@NotNull ColoredTreeCellRenderer renderer) {
    renderer.append(myLinkedText.getTextBefore(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    String linkedText = myLinkedText.getLinkText();
    if (!StringUtil.isEmptyOrSpaces(linkedText)) {
      renderer.append(" ");
      renderer.append(myLinkedText.getLinkText(), SimpleTextAttributes.SYNTHETIC_ATTRIBUTES, this);
    }
    renderer.append(myLinkedText.getTextAfter(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }
}