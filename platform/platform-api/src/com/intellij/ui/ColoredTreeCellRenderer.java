// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextDelegateWithContextMenu;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

public abstract class ColoredTreeCellRenderer extends SimpleColoredComponent implements TreeCellRenderer {
  private static final Logger LOG = Logger.getInstance(ColoredTreeCellRenderer.class);

  private static final Icon LOADING_NODE_ICON = JBUIScale.scaleIcon(EmptyIcon.create(8, 16));

  /**
   * Defines whether the tree is selected or not
   */
  protected boolean mySelected;
  /**
   * Defines whether the tree has focus or not
   */
  private boolean myFocused;
  private boolean myFocusedCalculated;

  protected boolean myUsedCustomSpeedSearchHighlighting;

  protected JTree myTree;

  private boolean myOpaque = true;

  private @Nullable @Nls String myAccessibleStatusText = null;

  @Override
  public final Component getTreeCellRendererComponent(JTree tree,
                                                      Object value,
                                                      boolean selected,
                                                      boolean expanded,
                                                      boolean leaf,
                                                      int row,
                                                      boolean hasFocus) {
    try {
      rendererComponentInner(tree, value, selected, expanded, leaf, row, hasFocus);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      try { LOG.error(e); } catch (Exception ignore) { }
    }
    return this;
  }

  private void rendererComponentInner(JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
    myTree = tree;

    clear();

    mySelected = selected;
    myFocusedCalculated = false;

    // We paint background if and only if tree path is selected and tree has focus.
    // If path is selected and tree is not focused then we just paint focused border.
    if (UIUtil.isFullRowSelectionLAF()) {
      setBackground(selected ? UIUtil.getTreeSelectionBackground(true) : null);
    }
    else if (WideSelectionTreeUI.isWideSelection(tree)) {
      setPaintFocusBorder(false);
      if (selected) {
        setBackground(RenderingUtil.getSelectionBackground(tree));
      }
      else {
        setBackground(null);
      }
    }
    else if (selected) {
      setPaintFocusBorder(true);
      if (isFocused()) {
        setBackground(UIUtil.getTreeSelectionBackground(true));
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
      setForeground(RenderingUtil.getForeground(tree, selected));
      setIcon(null);
    }

    if (WideSelectionTreeUI.isWideSelection(tree)) {
      super.setOpaque(false);  // avoid erasing Nimbus focus frame
      super.setIconOpaque(false);
    }
    else {
      super.setOpaque(myOpaque || selected && hasFocus || selected && isFocused()); // draw selection background even for non-opaque tree
    }
    customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);

    if (!myUsedCustomSpeedSearchHighlighting && !(value instanceof LoadingNode)) {
      SpeedSearchUtil.applySpeedSearchHighlightingFiltered(tree, value, this, true, selected);
    }
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
    if (mySelected && isFocused() && JBUI.CurrentTheme.Tree.Selection.forceFocusedSelectionForeground()) {
      super.append(fragment, new SimpleTextAttributes(attributes.getStyle(), UIUtil.getTreeSelectionForeground(true)), isMainText);
    }
    else {
      super.append(fragment, attributes, isMainText);
    }
  }

  @Override
  protected void revalidateAndRepaint() {
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

  @ApiStatus.Experimental
  public void setAccessibleStatusText(@Nullable @Nls String accessibleStatusText) {
    myAccessibleStatusText = accessibleStatusText;
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleColoredTreeCellRendererWithContextMenu();
    }
    return accessibleContext;
  }

  protected class AccessibleColoredTreeCellRendererWithContextMenu extends AccessibleContextDelegateWithContextMenu {
    public AccessibleColoredTreeCellRendererWithContextMenu(AccessibleColoredTreeCellRenderer context) {
      super(context);
    }

    public AccessibleColoredTreeCellRendererWithContextMenu() {
      super(new AccessibleColoredTreeCellRenderer());
    }

    @Override
    protected void doShowContextMenu() {
      ActionManager.getInstance().tryToExecute(ActionManager.getInstance().getAction("ShowPopupMenu"), null, null, null, true);
    }

    @Override
    protected Container getDelegateParent() {
      return getParent();
    }
  }

  protected class AccessibleColoredTreeCellRenderer extends AccessibleSimpleColoredComponent {
    @Override
    public String getAccessibleName() {
      String name = getOriginalAccessibleName();

      if (myAccessibleStatusText != null && !myAccessibleStatusText.isEmpty()) {
        if (name == null) {
          name = myAccessibleStatusText;
        }
        else {
          if (!name.endsWith(",")) name += ",";
          name += " " + myAccessibleStatusText + ".";
        }
      }

      return name;
    }

    protected @Nullable @Nls String getOriginalAccessibleName() {
      return super.getAccessibleName();
    }
  }

  // The following method are overridden for performance reasons.
  // See the Implementation Note for more information.
  // javax.swing.tree.DefaultTreeCellRenderer
  // javax.swing.DefaultListCellRenderer

  @Override
  public void validate() {
  }

  @Override
  public void invalidate() {
  }

  @Override
  public void revalidate() {
  }

  @Override
  public void repaint(long tm, int x, int y, int width, int height) {
  }

  @Override
  protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
  }

  @Override
  public void firePropertyChange(String propertyName, byte oldValue, byte newValue) {
  }

  @Override
  public void firePropertyChange(String propertyName, char oldValue, char newValue) {
  }

  @Override
  public void firePropertyChange(String propertyName, short oldValue, short newValue) {
  }

  @Override
  public void firePropertyChange(String propertyName, int oldValue, int newValue) {
  }

  @Override
  public void firePropertyChange(String propertyName, long oldValue, long newValue) {
  }

  @Override
  public void firePropertyChange(String propertyName, float oldValue, float newValue) {
  }

  @Override
  public void firePropertyChange(String propertyName, double oldValue, double newValue) {
  }

  @Override
  public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
  }
}
