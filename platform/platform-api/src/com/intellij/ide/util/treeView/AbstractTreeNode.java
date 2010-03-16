/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.Map;

public abstract class AbstractTreeNode<T> extends PresentableNodeDescriptor implements NavigationItem, Queryable.Contributor {
  private AbstractTreeNode myParent;
  private T myValue;
  private NodeDescriptor myParentDescriptor;

  protected AbstractTreeNode(Project project, T value) {
    super(project, null);
    setValue(value);
  }

  @NotNull
  public abstract Collection<? extends AbstractTreeNode> getChildren();


  protected boolean hasProblemFileBeneath() {
    return false;
  }

  protected boolean valueIsCut() {
    return CopyPasteManager.getInstance().isCutElement(getValue());
  }

  public PresentableNodeDescriptor getChildToHighlightAt(int index) {
    final Collection<? extends AbstractTreeNode> kids = getChildren();
    int i = 0;
    for (final AbstractTreeNode kid : kids) {
      if (i == index) return kid;
      i++;
    }

    return null;
  }

  @Override
  protected void postprocess(PresentationData presentation) {
    if (hasProblemFileBeneath() ) {
      presentation.setAttributesKey(CodeInsightColors.ERRORS_ATTRIBUTES);
    }

    Color fgColor = getFileStatus().getColor();

    if (valueIsCut()) {
      fgColor = CopyPasteManager.CUT_COLOR;
    }

    if (presentation.getForcedTextForeground() == null) {
      presentation.setForcedTextForeground(fgColor);
    }

    if (hasProblemFileBeneath() ) {
      presentation.setAttributesKey(CodeInsightColors.ERRORS_ATTRIBUTES);
    }
  }

  protected boolean shouldUpdateData() {
    return !myProject.isDisposed() && getValue() != null;
  }


  public boolean isAlwaysShowPlus() {
    return false;
  }

  public boolean isAlwaysLeaf() {
    return false;   
  }

  public boolean isAlwaysExpand() {
    return false;
  }

  @Nullable
  public final Object getElement() {
    return getValue() != null ? this : null;
  }

  public boolean equals(Object object) {
    return object instanceof AbstractTreeNode && Comparing.equal(getValue(), ((AbstractTreeNode)object).getValue());
  }

  public int hashCode() {
    return getValue() == null ? 0 : getValue().hashCode();
  }

  public final AbstractTreeNode getParent() {
    return myParent;
  }

  public final void setParent(AbstractTreeNode parent) {
    myParent = parent;
    myParentDescriptor = parent;
  }

  public final AbstractTreeNode setParentDescriptor(NodeDescriptor parentDescriptor) {
    myParentDescriptor = parentDescriptor;
    return this;                        
  }

  public final NodeDescriptor getParentDescriptor() {
    return myParentDescriptor;
  }

  public final T getValue() {
    return myValue;
  }

  public final void setValue(T value) {
    myValue = value;
  }

  @Nullable
  public String toTestString(Queryable.PrintInfo printInfo) {
    if (getValue() instanceof Queryable) {
      String text = Queryable.Util.print((Queryable)getValue(), printInfo, this);
      if (text != null) return text;
    }

    return getTestPresentation();
  }

  public void apply(Map<String, String> info) {
  }

  /**
   * @deprecated use toTestString
   * @return
   */
  @Nullable
  @NonNls public String getTestPresentation() {
    if (myName != null) {
      return myName;
    } else if (getValue() != null){
      return getValue().toString();
    } else {
      return null;
    }
  }


  public FileStatus getFileStatus() {
    return FileStatus.NOT_CHANGED;
  }

  public String getName() {
    return myName;
  }

  public void navigate(boolean requestFocus) {
  }

  public boolean canNavigate() {
    return false;
  }

  public boolean canNavigateToSource() {
    return false;
  }

  @Nullable
  protected final Object getParentValue() {
    AbstractTreeNode parent = getParent();
    return parent == null ? null : parent.getValue();
  }


  public boolean canRepresent(final Object element) {
    return Comparing.equal(getValue(), element);
  }

  /**
   * @deprecated use {@link #getPresentation()} instead
   */
  protected String getToolTip() {
    return getPresentation().getTooltip();
  }

  /**
   * @deprecated use {@link #getPresentation()} instead
   */
  @Nullable
  public TextAttributesKey getAttributesKey() {
    return getPresentation().getTextAttributesKey();
  }

  /**
   * @deprecated use {@link #getPresentation()} instead
   */
  @Nullable
  public String getLocationString() {
    return getPresentation().getLocationString();
  }


}
