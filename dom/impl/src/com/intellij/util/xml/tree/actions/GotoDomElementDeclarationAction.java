/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.util.xml.tree.DomModelTreeView;
import com.intellij.util.xml.tree.BaseDomElementNode;
import com.intellij.util.xml.DomElementsNavigationManager;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementNavigateProvider;
import com.intellij.ui.treeStructure.SimpleNode;

/**
 * User: Sergey.Vasiliev
 */
public class GotoDomElementDeclarationAction extends BaseDomTreeAction {

  public void actionPerformed(AnActionEvent e, DomModelTreeView treeView) {
    final SimpleNode simpleNode = treeView.getTree().getSelectedNode();

    if(simpleNode instanceof BaseDomElementNode) {
      final DomElement domElement = ((BaseDomElementNode)simpleNode).getDomElement();
      final DomElementNavigateProvider provider =
        DomElementsNavigationManager.getManager(domElement.getManager().getProject()).getDomElementsNavigateProvider(DomElementsNavigationManager.DEFAULT_PROVIDER_NAME);

      provider.navigate(domElement, true);

    }
  }

  public void update(AnActionEvent e, DomModelTreeView treeView) {
     e.getPresentation().setText("Go to declaration");
  }
}
