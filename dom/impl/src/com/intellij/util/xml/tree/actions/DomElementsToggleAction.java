/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.tree.BaseDomElementNode;
import com.intellij.util.xml.tree.DomModelTreeView;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * User: Sergey.Vasiliev
 */
public class DomElementsToggleAction extends ToggleAction {
  private DomModelTreeView myTreeView;
  private Class myClass;
  private Icon myIcon;
  private String myText;


  public DomElementsToggleAction(final DomModelTreeView treeView, final Class aClass) {
    myTreeView = treeView;
    myClass = aClass;

    myIcon = ElementPresentationManager.getIcon(myClass);
    if (myIcon == null) {
      myIcon = IconLoader.getIcon("/nodes/pointcut.png");
    }
    myText = ElementPresentationManager.getTypeName(myClass);

    if(getHiders() == null) myTreeView.getRootElement().getRoot().getFile().putUserData(BaseDomElementNode.TREE_NODES_HIDERS_KEY, new HashMap<Class, Boolean>());

    if(getHiders().get(myClass) == null) getHiders().put(myClass, true);
  }

  public void update(final AnActionEvent e) {
    super.update(e);

    e.getPresentation().setIcon(myIcon);
    e.getPresentation().setText((getHiders().get(myClass) ? "Hide ":"Show ")+myText);

    e.getPresentation().setEnabled(getHiders() != null && getHiders().get(myClass)!=null);
  }

  public boolean isSelected(AnActionEvent e) {
    return getHiders().get(myClass);
  }

  private Map<Class, Boolean> getHiders() {
    return myTreeView.getRootElement().getRoot().getFile().getUserData(BaseDomElementNode.TREE_NODES_HIDERS_KEY);
  }

  public void setSelected(AnActionEvent e, boolean state) {
    getHiders().put(myClass, state);
    myTreeView.getBuilder().updateFromRoot(true);
  }
}

