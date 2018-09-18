// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.push.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.changes.issueLinks.LinkMouseListenerBase;
import com.intellij.ui.CheckboxTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;

public class VcsBranchEditorListener extends LinkMouseListenerBase {

  private static final Logger LOG = Logger.getInstance(VcsBranchEditorListener.class);
  private final CheckboxTree.CheckboxTreeCellRenderer myRenderer;
  private VcsLinkedTextComponent myUnderlined;

  public VcsBranchEditorListener(final CheckboxTree.CheckboxTreeCellRenderer renderer) {
    myRenderer = renderer;
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    Component component = (Component)e.getSource();
    Object tag = getTagAt(e);
    boolean shouldRepaint = false;
    if (myUnderlined != null) {
      myUnderlined.setUnderlined(false);
      myUnderlined = null;
      shouldRepaint = true;
    }
    if (tag instanceof VcsLinkedTextComponent) {
      component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      VcsLinkedTextComponent linkedText = (VcsLinkedTextComponent)tag;
      linkedText.setUnderlined(true);
      myUnderlined = linkedText;
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

  @Override
  protected void handleTagClick(@Nullable final Object tag, @NotNull MouseEvent event) {
    if (tag instanceof VcsLinkedTextComponent) {
      VcsLinkedTextComponent textWithLink = (VcsLinkedTextComponent)tag;
      final TreePath path = myRenderer.getTextRenderer().getTree().getPathForLocation(event.getX(), event.getY());
      if (path == null) return; //path could not be null if tag not null; see com.intellij.dvcs.push.ui.PushLogTreeUtil.getTagAtForRenderer
      Object node = path.getLastPathComponent();
      if ((!(node instanceof DefaultMutableTreeNode))) {
        LOG.warn("Incorrect last path component: " + node);
        return;
      }
      textWithLink.fireOnClick((DefaultMutableTreeNode)node, event);
    }
    if (tag instanceof Runnable) {
      ((Runnable)tag).run();
    }
  }
}
