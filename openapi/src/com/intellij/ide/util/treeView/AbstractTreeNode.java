package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.pom.Navigatable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

public abstract class AbstractTreeNode<Value> extends NodeDescriptor implements NavigationItem, Navigatable {
  private AbstractTreeNode myParent;
  private Value myValue;
  private NodeDescriptor myParentDescriptor;
  protected String myLocationString;

  public AbstractTreeNode(Project project, Value value) {
    super(project, null);
    setValue(value);
  }

  public abstract Collection<AbstractTreeNode> getChildren();

  public final boolean update() {
    PresentationData presentation = getUpdatedData();

    Icon openIcon = presentation.getIcon(true);
    Icon closedIcon = presentation.getIcon(false);
    String name = presentation.getPresentableText();
    String locationString = presentation.getLocationString();
    Color color = getFileStatus().getColor();
    if (valueIsCut()) {
      color = CopyPasteManager.CUT_COLOR;
    }

    boolean updated = false;
    updated = !Comparing.equal(new Object[]{myOpenIcon, myClosedIcon, myName, myLocationString, myColor},
                         new Object[]{openIcon, closedIcon, name, locationString, color});

    myOpenIcon = openIcon;
    myClosedIcon = closedIcon;
    myName = name;
    myLocationString = locationString;
    myColor = color;

    return updated;
  }

  protected boolean valueIsCut() {
    return CopyPasteManager.getInstance().isCutElement(getValue());
  }

  private PresentationData getUpdatedData() {
    PresentationData presentation = new PresentationData();
    if (shouldUpdateData()) {
      update(presentation);
    }
    return presentation;
  }

  protected boolean shouldUpdateData() {
    return getValue() != null;
  }

  protected abstract void update(PresentationData presentation);

  public boolean isAlwaysShowPlus() {
    return false;
  }

  public boolean isAlwaysExpand() {
    return false;
  }

  public final Object getElement() {
    return this;
  }

  public final boolean equals(Object object) {
    if (!(object instanceof AbstractTreeNode)) return false;
    return Comparing.equal(getValue(), ((AbstractTreeNode)object).getValue());
  }

  public final int hashCode() {
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

  public final Value getValue() {
    return myValue;
  }

  public final Project getProject() {
    return myProject;
  }

  public final void setValue(Value value) {
    myValue = value;
  }

  public String getTestPresentation() {
    return myValue == null ? "" : myValue.toString();
  }

  public ItemPresentation getPresentation() {
    return getUpdatedData();
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

  protected final Object getParentValue() {
    return getParent() == null ? null : getParent().getValue();
  }
}
