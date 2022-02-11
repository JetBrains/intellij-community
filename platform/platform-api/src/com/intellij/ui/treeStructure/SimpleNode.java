// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.treeStructure;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.tree.LeafState;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.ComparableObject;
import com.intellij.util.ui.update.ComparableObjectCheck;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class SimpleNode extends PresentableNodeDescriptor implements ComparableObject, LeafState.Supplier {

  protected static final SimpleNode[] NO_CHILDREN = new SimpleNode[0];

  protected SimpleNode(Project project) {
    this(project, null);
  }

  protected SimpleNode(Project project, @Nullable NodeDescriptor parentDescriptor) {
    super(project, parentDescriptor);
    myName = "";
  }

  protected SimpleNode(SimpleNode parent) {
    this(parent == null ? null : parent.myProject, parent);
  }

  @Override
  public PresentableNodeDescriptor getChildToHighlightAt(int index) {
    return getChildAt(index);
  }

  protected SimpleNode() {
    super(null, null);
  }

  public String toString() {
    return getName();
  }

  @Override
  public int getWeight() {
    return 10;
  }

  protected SimpleTextAttributes getErrorAttributes() {
    return new SimpleTextAttributes(SimpleTextAttributes.STYLE_WAVED, getColor(), JBColor.RED);
  }

  protected SimpleTextAttributes getPlainAttributes() {
    return new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, getColor());
  }

  private FileStatus getFileStatus() {
    return FileStatus.NOT_CHANGED;
  }

  @Nullable
  protected Object updateElement() {
    return getElement();
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    Object newElement = updateElement();
    if (getElement() != newElement) {
      presentation.setChanged(true);
    }
    if (newElement == null) return;

    Color oldColor = myColor;
    String oldName = myName;
    Icon oldIcon = getIcon();
    List<ColoredFragment> oldFragments = new ArrayList<>(presentation.getColoredText());

    myColor = UIUtil.getTreeForeground();
    updateFileStatus();

    doUpdate();

    myName = getName();
    presentation.setPresentableText(myName);

    presentation.setChanged(!Arrays.equals(new Object[]{getIcon(), myName, oldFragments, myColor},
                                           new Object[]{oldIcon, oldName, oldFragments, oldColor}));

    presentation.setForcedTextForeground(myColor);
    presentation.setIcon(getIcon());
  }

  protected void updateFileStatus() {
    assert getFileStatus() != null : getClass().getName() + ' ' + toString();

    Color fileStatusColor = getFileStatus().getColor();
    if (fileStatusColor != null) {
      myColor = fileStatusColor;
    }
  }

  /**
   * @deprecated use {@link #getTemplatePresentation()} to set constant presentation right in node's constructor
   * or update presentation dynamically by defining {@link #update(PresentationData)}
   */
  @Deprecated(forRemoval = true)
  public final void setNodeText(String text, String tooltip, boolean hasError) {
    clearColoredText();
    SimpleTextAttributes attributes = hasError ? getErrorAttributes() : getPlainAttributes();
    getTemplatePresentation().addText(new ColoredFragment(text, tooltip, attributes));
  }

  /**
   * @deprecated use {@link #getTemplatePresentation()} to set constant presentation right in node's constructor
   * or update presentation dynamically by defining {@link #update(PresentationData)}
   */
  @Deprecated(forRemoval = true)
  public final void setPlainText(String aText) {
    clearColoredText();
    addPlainText(aText);
  }

  /**
   * @deprecated use {@link #getTemplatePresentation()} to set constant presentation right in node's constructor
   * or update presentation dynamically by defining {@link #update(PresentationData)}
   */
  @Deprecated(forRemoval = true)
  public final void addPlainText(String aText) {
    getTemplatePresentation().addText(new ColoredFragment(aText, getPlainAttributes()));
  }

  /**
   * @deprecated use {@link #getTemplatePresentation()} to set constant presentation right in node's constructor
   * or update presentation dynamically by defining {@link #update(PresentationData)}
   */
  @Deprecated(forRemoval = true)
  public final void clearColoredText() {
    getTemplatePresentation().clearText();
  }

  /**
   * @deprecated use {@link #getTemplatePresentation()} to set constant presentation right in node's constructor
   * or update presentation dynamically by defining {@link #update(PresentationData)}
   */
  @Deprecated(forRemoval = true)
  public final void addColoredFragment(String aText, SimpleTextAttributes aAttributes) {
    addColoredFragment(aText, null, aAttributes);
  }

  /**
   * @deprecated use {@link #getTemplatePresentation()} to set constant presentation right in node's constructor
   * or update presentation dynamically by defining {@link #update(PresentationData)}
   */
  @Deprecated(forRemoval = true)
  public final void addColoredFragment(String aText, String toolTip, SimpleTextAttributes aAttributes) {
    getTemplatePresentation().addText(new ColoredFragment(aText, toolTip, aAttributes));
  }

  protected void doUpdate() {
  }

  @Override
  public Object getElement() {
    return this;
  }

  public final SimpleNode getParent() {
    return (SimpleNode)getParentDescriptor();
  }

  public int getIndex(SimpleNode child) {
    final SimpleNode[] kids = getChildren();
    for (int i = 0; i < kids.length; i++) {
      SimpleNode each = kids[i];
      if (each.equals(child)) return i;
    }

    return -1;
  }

  public abstract SimpleNode @NotNull [] getChildren();

  public void accept(@NotNull SimpleNodeVisitor visitor) {
    visitor.accept(this);
  }

  public void handleSelection(SimpleTree tree) {
  }

  public void handleDoubleClickOrEnter(SimpleTree tree, InputEvent inputEvent) {
  }

  @NotNull
  @Override
  public LeafState getLeafState() {
    if (isAlwaysShowPlus()) return LeafState.NEVER;
    if (isAlwaysLeaf()) return LeafState.ALWAYS;
    return LeafState.DEFAULT;
  }

  public boolean isAlwaysShowPlus() {
    return false;
  }

  public boolean isAutoExpandNode() {
    return false;
  }

  public boolean isAlwaysLeaf() {
    return false;
  }

  public boolean shouldHaveSeparator() {
    return false;
  }

  /**
   * @deprecated use {@link #getTemplatePresentation()} to set constant presentation right in node's constructor
   * or update presentation dynamically by defining {@link #update(PresentationData)}
   */
  @Deprecated(forRemoval = true)
  public void setUniformIcon(Icon aIcon) {
    setIcon(aIcon);
  }

  @Override
  public Object @NotNull [] getEqualityObjects() {
    return NONE;
  }

  public int getChildCount() {
    return getChildren().length;
  }

  public SimpleNode getChildAt(final int i) {
    return getChildren()[i];
  }


  public final boolean equals(Object o) {
    return ComparableObjectCheck.equals(this, o);
  }

  public final int hashCode() {
    return ComparableObjectCheck.hashCode(this, super.hashCode());
  }

}
