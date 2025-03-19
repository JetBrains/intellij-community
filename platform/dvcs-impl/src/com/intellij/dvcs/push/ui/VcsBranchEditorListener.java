// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.changes.issueLinks.LinkMouseListenerBase;
import com.intellij.ui.CheckboxTree;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;

@ApiStatus.Internal
public final class VcsBranchEditorListener extends LinkMouseListenerBase {
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
    if (tag instanceof VcsLinkedTextComponent linkedText) {
      component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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

  @Override
  protected @Nullable Object getTagAt(final @NotNull MouseEvent e) {
    return PushLogTreeUtil.getTagAtForRenderer(myRenderer, e);
  }

  @Override
  protected void handleTagClick(final @Nullable Object tag, @NotNull MouseEvent event) {
    if (tag instanceof VcsLinkedTextComponent textWithLink) {
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
