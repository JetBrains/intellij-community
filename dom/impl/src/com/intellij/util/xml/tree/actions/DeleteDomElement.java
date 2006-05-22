/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.tree.actions;

import com.intellij.util.xml.ElementPresentation;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.xml.tree.BaseDomElementNode;
import com.intellij.util.xml.tree.DomModelTreeView;
import com.intellij.ui.treeStructure.SimpleNode;

/**
 * User: Sergey.Vasiliev
 */
public class DeleteDomElement extends BaseDomTreeAction {


  public DeleteDomElement() {
  }

  public DeleteDomElement(final DomModelTreeView treeView) {
     super(treeView);
  }


  public void actionPerformed(AnActionEvent e, DomModelTreeView treeView) {
      if (treeView.getTree().getSelectedNode() instanceof BaseDomElementNode) {
       final BaseDomElementNode selectedNode = (BaseDomElementNode)treeView.getTree().getSelectedNode();

        new WriteCommandAction(selectedNode.getDomElement().getParent().getManager().getProject()) {
         protected void run(final Result result) throws Throwable {

           selectedNode.getDomElement().undefine();
         }
       }.execute();
     }
  }


  public void update(AnActionEvent e, DomModelTreeView treeView) {
    final SimpleNode selectedNode = treeView.getTree().getSelectedNode();

    boolean enabled = false;
    if (selectedNode instanceof BaseDomElementNode) {
      if (((BaseDomElementNode)selectedNode).getDomElement().getXmlElement() != null) {
        enabled = true;
      }
    }

    e.getPresentation().setEnabled(enabled);

    if (enabled) {
      final ElementPresentation presentation = ((BaseDomElementNode)selectedNode).getDomElement().getPresentation();
      e.getPresentation().setText("Delete " + presentation.getTypeName()+(presentation.getElementName() == null? "": ": " + presentation.getElementName()));
    } else {
      e.getPresentation().setText("Delete");
    }

    e.getPresentation().setIcon(IconLoader.getIcon("/general/remove.png"));
  }
}
