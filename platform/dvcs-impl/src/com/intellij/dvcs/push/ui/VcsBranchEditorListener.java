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

import com.intellij.openapi.vcs.changes.issueLinks.LinkMouseListenerBase;
import com.intellij.ui.CheckboxTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;

public class VcsBranchEditorListener extends LinkMouseListenerBase {
  private final CheckboxTree.CheckboxTreeCellRenderer myRenderer;
  private VcsLinkedText underlined;

  public VcsBranchEditorListener(final CheckboxTree.CheckboxTreeCellRenderer renderer) {
    myRenderer = renderer;
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    Component component = (Component)e.getSource();
    Object tag = getTagAt(e);
    boolean shouldRepaint = false;
    if (underlined != null) {
      underlined.setUnderlined(false);
      shouldRepaint = true;
    }
    if (tag != null && tag instanceof VcsLinkedText) {
      component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      VcsLinkedText linkedText = (VcsLinkedText)tag;
      linkedText.setUnderlined(true);
      underlined = linkedText;
      shouldRepaint = true;
    }
    else {
      super.mouseMoved(e);
    }
    if (shouldRepaint) {
      myRenderer.getTextRenderer().getTree().repaint();
    }
  }

  @Nullable
  @Override
  protected Object getTagAt(@NotNull final MouseEvent e) {
    return PushLogTreeUtil.getTagAtForRenderer(myRenderer, e);
  }

  protected void handleTagClick(@Nullable final Object tag, @NotNull MouseEvent event) {
    if (tag instanceof VcsLinkedText) {
      VcsLinkedText textWithLink = (VcsLinkedText)tag;
      final TreePath path = myRenderer.getTextRenderer().getTree().getPathForLocation(event.getX(), event.getY());
      if (path == null) return;
      Object node = path.getLastPathComponent();
      if (node == null || (!(node instanceof DefaultMutableTreeNode))) return;
      textWithLink.fireOnClick((DefaultMutableTreeNode)node, event);
    }
    if (tag instanceof Runnable) {
      ((Runnable)tag).run();
    }
  }
}
