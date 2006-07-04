/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.xml.ElementPresentation;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.tree.BaseDomElementNode;
import com.intellij.util.xml.tree.DomModelTreeView;

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
    if (treeView.getTree().getSelectedNode()instanceof BaseDomElementNode) {
      final BaseDomElementNode selectedNode = (BaseDomElementNode)treeView.getTree().getSelectedNode();

      final DomElement domElement = selectedNode.getDomElement();
      new WriteCommandAction(domElement.getManager().getProject(), domElement.getXmlTag().getContainingFile()) {
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
      final DomElement domElement = ((BaseDomElementNode)selectedNode).getDomElement();
      if (domElement.getXmlElement() != null && !domElement.equals(domElement.getRoot().getRootElement())) {
        enabled = true;
      }
    }

    e.getPresentation().setEnabled(enabled);

    final String removeString = ApplicationBundle.message("action.remove");
    if (enabled) {
      final ElementPresentation presentation = ((BaseDomElementNode)selectedNode).getDomElement().getPresentation();

      e.getPresentation().setText(removeString + " " +presentation.getTypeName() + (presentation.getElementName() == null ? "" : ": " + presentation.getElementName()));
    }
    else {
      e.getPresentation().setText(removeString);
    }

    e.getPresentation().setIcon(IconLoader.getIcon("/general/remove.png"));
  }
}
