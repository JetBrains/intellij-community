// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.presentation.FilePresentationService;
import com.intellij.psi.PsiElement;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.ui.tabs.FileColorManagerImpl;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class ProjectViewTree extends DnDAwareTree {
  private static final Logger LOG = Logger.getInstance(ProjectViewTree.class);

  /**
   * @deprecated use another constructor instead
   */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("unused")
  protected ProjectViewTree(Project project, TreeModel model) {
    this(model);
  }

  public ProjectViewTree(TreeModel model) {
    super((TreeModel)null);
    setLargeModel(true);
    setModel(model);
    setCellRenderer(createCellRenderer());
    HintUpdateSupply.installDataContextHintUpdateSupply(this);
  }

  /**
   * @return custom renderer for tree nodes
   */
  @NotNull
  protected TreeCellRenderer createCellRenderer() {
    return new ProjectViewRenderer();
  }

  /**
   * @deprecated Not every tree employs {@link DefaultMutableTreeNode} so
   * use {@link #getSelectionPaths()} or {@link TreeUtil#getSelectedPathIfOne(JTree)} directly.
   */
  @Deprecated(forRemoval = true)
  public DefaultMutableTreeNode getSelectedNode() {
    TreePath path = TreeUtil.getSelectedPathIfOne(this);
    return path == null ? null : ObjectUtils.tryCast(path.getLastPathComponent(), DefaultMutableTreeNode.class);
  }

  @Override
  public void setToggleClickCount(int count) {
    if (count != 2) LOG.info(new IllegalStateException("setToggleClickCount: unexpected count = " + count));
    super.setToggleClickCount(count);
  }

  @Override
  public boolean isFileColorsEnabled() {
    return isFileColorsEnabledFor(this);
  }

  public static boolean isFileColorsEnabledFor(JTree tree) {
    boolean enabled = FileColorManagerImpl._isEnabled() && FileColorManagerImpl._isEnabledForProjectView();
    boolean opaque = tree.isOpaque();
    if (enabled && opaque) {
      tree.setOpaque(false);
    }
    else if (!enabled && !opaque) {
      tree.setOpaque(true);
    }
    return enabled;
  }

  @Nullable
  @Override
  public Color getFileColorFor(Object object) {
    if (object instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)object;
      object = node.getUserObject();
    }
    if (object instanceof PresentableNodeDescriptor) {
      return ((PresentableNodeDescriptor<?>)object).getPresentation().getBackground();
    }
    return null;
  }

  @Nullable
  public static Color getColorForElement(@Nullable PsiElement psi) {
    if (psi == null || !psi.isValid()) {
      return null;
    }
    Project project = psi.getProject();
    return FilePresentationService.getInstance(project).getFileBackgroundColor(psi);
  }
}
