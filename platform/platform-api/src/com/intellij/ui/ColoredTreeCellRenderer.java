// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.util.treeView.AbstractTreeUi;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

/**
 * @author Vladimir Kondratyev
 */
public abstract class ColoredTreeCellRenderer extends SimpleColoredComponent implements TreeCellRenderer{
  private static final Icon LOADING_NODE_ICON = JBUI.scale(EmptyIcon.create(8, 16));

  /**
   * Defines whether the tree is selected or not
   */
  protected boolean mySelected;
  /**
   * Defines whether the tree has focus or not
   */
  private boolean myFocused;
  private boolean myFocusedCalculated;

  protected boolean myUsedCustomSpeedSearchHighlighting = false;

  protected JTree myTree;

  private boolean myOpaque = true;
  @Override
  public final Component getTreeCellRendererComponent(JTree tree,
                                                      Object value,
                                                      boolean selected,
                                                      boolean expanded,
                                                      boolean leaf,
                                                      int row,
                                                      boolean hasFocus){
    myTree = tree;

    clear();

    mySelected = selected;
    myFocusedCalculated = false;

    // We paint background if and only if tree path is selected and tree has focus.
    // If path is selected and tree is not focused then we just paint focused border.
    if (UIUtil.isFullRowSelectionLAF()) {
      setBackground(selected ? UIUtil.getTreeSelectionBackground() : null);
    }
    else if (WideSelectionTreeUI.isWideSelection(tree)) {
      setPaintFocusBorder(false);
      if (selected) {
        setBackground(hasFocus ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeUnfocusedSelectionBackground());
      }
    }
    else if (selected) {
      setPaintFocusBorder(true);
      if (isFocused()) {
        setBackground(UIUtil.getTreeSelectionBackground());
      }
      else {
        setBackground(null);
      }
    }
    else {
      setBackground(null);
    }

    if (value instanceof LoadingNode) {
      setForeground(JBColor.GRAY);
      setIcon(LOADING_NODE_ICON);
    }
    else {
      setForeground(tree.getForeground());
      setIcon(null);
    }

    if (UIUtil.isUnderGTKLookAndFeel()){
      super.setOpaque(false);  // avoid nasty background
      super.setIconOpaque(false);
    }
    else if (WideSelectionTreeUI.isWideSelection(tree)) {
      super.setOpaque(false);  // avoid erasing Nimbus focus frame
      super.setIconOpaque(false);
    }
    else {
      super.setOpaque(myOpaque || selected && hasFocus || selected && isFocused()); // draw selection background even for non-opaque tree
    }
    customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);

    if (!myUsedCustomSpeedSearchHighlighting && !AbstractTreeUi.isLoadingNode(value)) {
      SpeedSearchUtil.applySpeedSearchHighlighting(tree, this, true, selected);
    }
    return this;
  }

  public JTree getTree() {
    return myTree;
  }

  protected final boolean isFocused() {
    if (!myFocusedCalculated) {
      myFocused = calcFocusedState();
      myFocusedCalculated = true;
    }
    return myFocused;
  }

  protected boolean calcFocusedState() {
    return myTree.hasFocus();
  }

  @Override
  public void setOpaque(boolean isOpaque) {
    myOpaque = isOpaque;
    super.setOpaque(isOpaque);
  }

  @Override
  public Font getFont() {
    Font font = super.getFont();

    // Cell renderers could have no parent and no explicit set font.
    // Take tree font in this case.
    if (font != null) return font;
    JTree tree = getTree();
    return tree != null ? tree.getFont() : null;
  }

  /**
   * When the item is selected then we use default tree's selection foreground.
   * It guaranties readability of selected text in any LAF.
   */
  @Override
  public void append(@NotNull @Nls String fragment, @NotNull SimpleTextAttributes attributes, boolean isMainText) {
    if (mySelected && isFocused()) {
      super.append(fragment, new SimpleTextAttributes(attributes.getStyle(), UIUtil.getTreeSelectionForeground()), isMainText);
    }
    else if (mySelected && UIUtil.isUnderAquaBasedLookAndFeel()) {
      super.append(fragment, new SimpleTextAttributes(attributes.getStyle(), UIUtil.getTreeForeground()), isMainText);
    }
    else {
      super.append(fragment, attributes, isMainText);
    }
  }

  @Override
  void revalidateAndRepaint() {
    // no need for this in a renderer
  }

  /**
   * This method is invoked only for customization of component.
   * All component attributes are cleared when this method is being invoked.
   */
  public abstract void customizeCellRenderer(@NotNull JTree tree,
                                             Object value,
                                             boolean selected,
                                             boolean expanded,
                                             boolean leaf,
                                             int row,
                                             boolean hasFocus);

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleColoredTreeCellRenderer();
    }
    return accessibleContext;
  }

  protected class AccessibleColoredTreeCellRenderer extends AccessibleSimpleColoredComponent {
  }
}
