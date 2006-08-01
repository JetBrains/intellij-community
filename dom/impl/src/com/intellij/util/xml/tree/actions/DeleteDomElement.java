/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ElementPresentation;
import com.intellij.util.xml.tree.BaseDomElementNode;
import com.intellij.util.xml.tree.DomFileElementNode;
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
    final SimpleNode selectedNode = treeView.getTree().getSelectedNode();

    if (selectedNode instanceof BaseDomElementNode) {

      if (selectedNode instanceof DomFileElementNode) {
        e.getPresentation().setVisible(false);
        return;
      }
      
      final DomElement domElement = ((BaseDomElementNode)selectedNode).getDomElement();

      final int ret = Messages.showOkCancelDialog(getPresentationText(selectedNode) + "?", ApplicationBundle.message("action.remove"),
                                                  Messages.getQuestionIcon());
      if (ret == 0) {
      new WriteCommandAction(domElement.getManager().getProject(), domElement.getRoot().getFile()) {
        protected void run(final Result result) throws Throwable {
          domElement.undefine();
        }
      }.execute();
      }
    }
  }

  public void update(AnActionEvent e, DomModelTreeView treeView) {
    final SimpleNode selectedNode = treeView.getTree().getSelectedNode();

    if (selectedNode instanceof DomFileElementNode) {
      e.getPresentation().setVisible(false);
      return;
    }

    boolean enabled = false;
    if (selectedNode instanceof BaseDomElementNode) {
      final DomElement domElement = ((BaseDomElementNode)selectedNode).getDomElement();
      if (domElement.isValid() && domElement.getXmlElement() != null && !domElement.equals(domElement.getRoot().getRootElement())) {
        enabled = true;
      }
    }

    e.getPresentation().setEnabled(enabled);


    if (enabled) {
      e.getPresentation().setText(getPresentationText(selectedNode));
    }
    else {
      e.getPresentation().setText(ApplicationBundle.message("action.remove"));
    }

    e.getPresentation().setIcon(IconLoader.getIcon("/general/remove.png"));
  }

  private static String getPresentationText(final SimpleNode selectedNode) {
    String removeString = ApplicationBundle.message("action.remove");
    final ElementPresentation presentation = ((BaseDomElementNode)selectedNode).getDomElement().getPresentation();
    removeString += " " + presentation.getTypeName() +
                                (presentation.getElementName() == null || presentation.getElementName().trim().length() == 0? "" : ": " + presentation.getElementName());
    return removeString;
  }
}
