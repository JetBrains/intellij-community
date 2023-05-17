// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.presentation.FilePresentationService;
import com.intellij.psi.PsiElement;
import com.intellij.toolWindow.InternalDecoratorImpl;
import com.intellij.toolWindow.ToolWindowHeader;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.ui.tabs.FileColorManagerImpl;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
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
public class ProjectViewTree extends DnDAwareTree implements SpeedSearchSupply.SpeedSearchLocator {

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

    // not required for Project View, and on start-up, if an editor component is not yet added, it can show confusing "Nothing to show"
    // a proper fix — wait ~300ms before showing empty status text, or investigating why "Project View" is rendered in the whole area,
    // including editor, are not safe for 231 and should be addressed separate
    getEmptyText().setText("");

    setLargeModel(true);
    setModel(model);
    setCellRenderer(createCellRenderer());
    HintUpdateSupply.installDataContextHintUpdateSupply(this);

    DataManager.registerDataProvider(this, new DataProvider() {
      @Override
      public @Nullable Object getData(@NotNull String dataId) {
          if (PlatformDataKeys.SPEED_SEARCH_LOCATOR.is(dataId)) {
            return ProjectViewTree.this;
          }
          return null;
        }
    });
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
  public boolean isFileColorsEnabled() {
    return isFileColorsEnabledFor(this);
  }

  @Override
  public @Nullable RelativeRectangle getSizeAndLocation(JComponent target) {
    if (target == this) {
      InternalDecoratorImpl tw = UIUtil.getParentOfType(InternalDecoratorImpl.class, this);
      if (tw != null) {
        ToolWindowHeader header = UIUtil.findComponentOfType(tw, ToolWindowHeader.class);
        if (header != null) {
          RelativePoint rp = new RelativePoint(header, new Point(-JBUI.scale(1), 0));
          Dimension d = new Dimension(header.getWidth() + 2 * JBUI.scale(1), header.getHeight());
          return new RelativeRectangle(rp, d);
        }
      }
    }
    return null;
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
    if (object instanceof DefaultMutableTreeNode node) {
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
