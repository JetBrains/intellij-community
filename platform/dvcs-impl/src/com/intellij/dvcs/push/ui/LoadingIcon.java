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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ImageLoader;
import com.intellij.util.ui.JBImageIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.image.ImageObserver;

class LoadingIcon extends JBImageIcon {

  private static final String LOADING_ICON = "/icons/loading.gif";
  private static final Logger LOG = Logger.getInstance(LoadingIcon.class);

  private final int myWidth;
  private final int myHeight;
  private final int myDeltaX;
  private final int myDeltaY;

  LoadingIcon(@NotNull Image image, int width, int height) {
    super(image);
    int myOriginalWidth = image.getWidth(null);
    int myOriginalHeight = image.getHeight(null);
    myWidth = Math.max(width, myOriginalWidth);
    myHeight = Math.max(height, myOriginalHeight);
    myDeltaX = (myWidth - myOriginalWidth) / 2 - 2;
    myDeltaY = (myHeight - myOriginalHeight) / 2 + 2;
  }

  @NotNull
  static LoadingIcon create(int width, int height) {
    Image image = ImageLoader.loadFromResource(LOADING_ICON);
    if (image == null) {
      LOG.error("Couldn't load image: " + LOADING_ICON);
      return createEmpty(width, height);
    }
    return new LoadingIcon(image, width, height);
  }

  @NotNull
  static LoadingIcon createEmpty(int width, int height) {
    return new LoadingIcon(UIUtil.createImage(width, height, Transparency.TRANSLUCENT), width, height);
  }

  void setObserver(@NotNull JTree tree, @NotNull TreeNode treeNode) {
    setImageObserver(new NodeImageObserver(tree, treeNode));
  }

  @Override
  public final synchronized void paintIcon(final Component c, final Graphics g, final int x, final int y) {
    super.paintIcon(c, g, x + myDeltaX, y + myDeltaY);
  }

  @Override
  public int getIconHeight() {
    return myHeight;
  }

  @Override
  public int getIconWidth() {
    return myWidth;
  }

  private static class NodeImageObserver implements ImageObserver {
    @NotNull private final JTree myTree;
    @NotNull private final DefaultTreeModel myModel;
    @NotNull private final TreeNode myNode;

    NodeImageObserver(@NotNull JTree tree, @NotNull TreeNode node) {
      myTree = tree;
      myModel = (DefaultTreeModel)tree.getModel();
      myNode = node;
    }

    public boolean imageUpdate(Image img, int flags, int x, int y, int w, int h) {
      if ((flags & (FRAMEBITS | ALLBITS)) != 0) {
        TreeNode[] pathToRoot = myModel.getPathToRoot(myNode);
        if (pathToRoot != null) {
          TreePath path = new TreePath(pathToRoot);
          Rectangle rect = myTree.getPathBounds(path);
          if (rect != null) {
            myTree.repaint(rect);
          }
        }
      }
      return (flags & (ALLBITS | ABORT)) == 0;
    }
  }
}
