package com.intellij.ide.util.treeView;

import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;

public abstract class NodeDescriptor<E> {
  protected final Project myProject;
  private final NodeDescriptor myParentDescriptor;

  protected String myName;
  protected Icon myOpenIcon;
  protected Icon myClosedIcon;
  protected Color myColor;

  private int myIndex = -1;

  public NodeDescriptor(Project project, NodeDescriptor parentDescriptor) {
    myProject = project;
    myParentDescriptor = parentDescriptor;
  }

  public NodeDescriptor getParentDescriptor() {
    return myParentDescriptor;
  }

  public int getIndex() {
    return myIndex;
  }

  public void setIndex(int index) {
    myIndex = index;
  }

  public abstract boolean update();

  public abstract E getElement();

  public String toString() {
    return myName;
  }

  public final Icon getOpenIcon() {
    return myOpenIcon;
  }

  public final Icon getClosedIcon() {
    return myClosedIcon;
  }

  public final Color getColor() {
    return myColor;
  }

  public boolean expandOnDoubleClick() {
    return true;
  }
}