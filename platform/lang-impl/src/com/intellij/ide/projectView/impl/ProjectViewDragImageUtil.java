// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.tree.TreePath;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

public final class ProjectViewDragImageUtil {
  private ProjectViewDragImageUtil() { }

  public static @Nullable Pair<Image, Point> createDraggedImage(@Nullable JTree tree) {
    if (tree == null) return null;
    try {
      ProjectViewRendererKt.setGrayedTextPaintingEnabled(false);
      final TreePath[] paths = tree.getSelectionPaths();
      if (paths == null || paths.length == 0) return null;
      var dragImageRows = createDragImageRows(tree, paths);
      BufferedImage image = paintDragImageRows(tree, dragImageRows);
      return new Pair<>(image, new Point());
    }
    finally {
      ProjectViewRendererKt.setGrayedTextPaintingEnabled(true);
    }
  }

  private static @NotNull ArrayList<DragImageRow> createDragImageRows(@NotNull JTree tree, @Nullable TreePath @NotNull [] paths) {
    var count = 0;
    int maxItemsToShow = paths.length < 20 ? paths.length : 10;
    var dragImageRows = new ArrayList<DragImageRow>();
    for (TreePath path : paths) {
      dragImageRows.add(new NodeRow(tree, path));
      count++;
      if (count > maxItemsToShow) {
        dragImageRows.add(new MoreFilesRow(tree, paths.length - maxItemsToShow));
        break;
      }
    }
    return dragImageRows;
  }

  private static @NotNull BufferedImage paintDragImageRows(@NotNull JTree tree, @NotNull ArrayList<DragImageRow> dragImageRows) {
    var totalHeight = 0;
    var maxWidth = 0;
    for (var row : dragImageRows) {
      var size = row.getSize();
      maxWidth = Math.max(maxWidth, size.width);
      totalHeight += size.height;
    }
    var gc = tree.getGraphicsConfiguration();
    BufferedImage image = ImageUtil.createImage(gc, maxWidth, totalHeight, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = (Graphics2D)image.getGraphics();
    try {
      for (var row : dragImageRows) {
        row.paint(g);
        g.translate(0, row.getSize().height);
      }
    }
    finally {
      g.dispose();
    }
    return image;
  }

  private abstract static class DragImageRow {
    abstract @NotNull Dimension getSize();
    abstract void paint(@NotNull Graphics2D g);
  }

  private static final class NodeRow extends DragImageRow {
    private final @NotNull JTree tree;
    private final @Nullable TreePath path;
    private @Nullable Dimension size;

    NodeRow(@NotNull JTree tree, @Nullable TreePath path) {
      this.tree = tree;
      this.path = path;
    }

    @Override
    @NotNull
    Dimension getSize() {
      var size = this.size;
      if (size == null) {
        size = getRenderer(tree, path).getPreferredSize();
        this.size = size;
      }
      return size;
    }

    @Override
    void paint(@NotNull Graphics2D g) {
      var renderer = getRenderer(tree, path);
      renderer.setSize(getSize());
      renderer.paint(g);
    }

    private static @NotNull Component getRenderer(@NotNull JTree tree, @Nullable TreePath path) {
      return tree.getCellRenderer().getTreeCellRendererComponent(
        tree,
        TreeUtil.getLastUserObject(path),
        false,
        false,
        true,
        tree.getRowForPath(path),
        false
      );
    }
  }

  private static final class MoreFilesRow extends DragImageRow {
    private final @NotNull JLabel moreLabel;

    MoreFilesRow(JTree tree, int moreItemsCount) {
      moreLabel = new JLabel(IdeBundle.message("label.more.files", moreItemsCount), EmptyIcon.ICON_16, SwingConstants.LEADING);
      moreLabel.setFont(tree.getFont());
      moreLabel.setSize(moreLabel.getPreferredSize());
    }

    @Override
    @NotNull
    Dimension getSize() {
      return moreLabel.getSize();
    }

    @Override
    void paint(@NotNull Graphics2D g) {
      moreLabel.paint(g);
    }
  }
}
