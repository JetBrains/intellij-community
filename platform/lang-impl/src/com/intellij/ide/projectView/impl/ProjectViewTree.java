// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.projectView.impl.nodes.BasePsiNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.UiCompatibleDataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.presentation.FilePresentationService;
import com.intellij.psi.PsiElement;
import com.intellij.toolWindow.InternalDecoratorImpl;
import com.intellij.toolWindow.ToolWindowHeader;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.ui.tabs.FileColorManagerImpl;
import com.intellij.ui.tree.ui.DefaultTreeUI;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.ApiStatus;
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
public class ProjectViewTree extends DnDAwareTree implements UiCompatibleDataProvider, SpeedSearchSupply.SpeedSearchLocator {

  private @Nullable ProjectViewDirectoryExpandDurationMeasurer expandMeasurer;

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

    ClientProperty.put(this, DefaultTreeUI.AUTO_EXPAND_FILTER, node -> {
      var obj = TreeUtil.getUserObject(node);
      if (obj instanceof BasePsiNode<?> pvNode) {
        var file = pvNode.getVirtualFile();
        return file != null && !file.isDirectory(); // true means "don't expand", so we only auto-expand directories
      }
      else if (obj instanceof AbstractTreeNode<?> abstractTreeNode) {
        return !abstractTreeNode.isAutoExpandAllowed();
      }
      else {
        return false;
      }
    });
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(PlatformDataKeys.SPEED_SEARCH_LOCATOR, this);
  }

  @Override
  public void setModel(TreeModel newModel) {
    var expandMeasurer = this.expandMeasurer;
    if (expandMeasurer != null) {
      expandMeasurer.detach(); // The entire model has changed, that expansion is not going to happen.
    }
    super.setModel(newModel);
  }

  @Override
  @ApiStatus.Internal
  public void startMeasuringExpandDuration(@NotNull TreePath path) {
    var model = getModel();
    if (model == null) {
      return;
    }
    var value = TreeUtil.getUserObject(path.getLastPathComponent());
    if (!(value instanceof PsiDirectoryNode)) {
      return; // Only measure real directory expansion, not, say, classes or external libraries.
    }
    var expandMeasurer = this.expandMeasurer;
    if (expandMeasurer != null) {
      expandMeasurer.detach();
    }
    expandMeasurer = new ProjectViewDirectoryExpandDurationMeasurer(model, path, () -> {
      this.expandMeasurer = null;
    });
    expandMeasurer.start();
    this.expandMeasurer = expandMeasurer;
  }

  @Override
  public void expandPath(TreePath path) {
    super.expandPath(path);
    var expandMeasurer = this.expandMeasurer;
    if (expandMeasurer != null) {
      expandMeasurer.checkExpanded(path);
    }
  }

  /**
   * @return custom renderer for tree nodes
   */
  protected @NotNull TreeCellRenderer createCellRenderer() {
    return new ProjectViewRenderer();
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

  @Override
  public @Nullable Color getFileColorFor(Object object) {
    if (object instanceof DefaultMutableTreeNode node) {
      object = node.getUserObject();
    }
    if (object instanceof PresentableNodeDescriptor) {
      return ((PresentableNodeDescriptor<?>)object).getPresentation().getBackground();
    }
    return null;
  }

  public static @Nullable Color getColorForElement(@Nullable PsiElement psi) {
    if (psi == null || !psi.isValid()) {
      return null;
    }
    Project project = psi.getProject();
    return FilePresentationService.getInstance(project).getFileBackgroundColor(psi);
  }
}
