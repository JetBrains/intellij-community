/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomReflectionUtil;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.tree.BaseDomElementNode;
import com.intellij.util.xml.tree.DomElementsGroupNode;
import com.intellij.util.xml.tree.DomModelTreeView;
import com.intellij.util.xml.ui.actions.AddDomElementAction;
import com.intellij.util.xml.ui.actions.DefaultAddAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.List;
import javax.swing.*;

/**
 * User: Sergey.Vasiliev
 */
public class AddElementInCollectionAction extends AddDomElementAction {
  private DomModelTreeView myTreeView;

  public AddElementInCollectionAction() {
  }

  public AddElementInCollectionAction(final DomModelTreeView treeView) {
    myTreeView = treeView;
  }

  protected DomModelTreeView getTreeView(AnActionEvent e) {
    if (myTreeView != null) return myTreeView;

    return (DomModelTreeView)e.getDataContext().getData(DomModelTreeView.DOM_MODEL_TREE_VIEW_KEY);
  }

  protected boolean isEnabled(final AnActionEvent e) {
    final DomModelTreeView treeView = getTreeView(e);

    final boolean enabled = treeView != null;
    e.getPresentation().setEnabled(enabled);

    return enabled;
  }


  protected void showPopup(final ListPopup groupPopup, final AnActionEvent e) {
    if (myTreeView == null) {
      if (e.getPlace().equals(DomModelTreeView.DOM_MODEL_TREE_VIEW_POPUP)) {
        groupPopup.showInCenterOf(getTreeView(e).getTree());
      }
      else {
        groupPopup.showInBestPositionFor(e.getDataContext());
      }
    }
    else {
      super.showPopup(groupPopup, e);
    }
  }

  @NotNull
  protected DomCollectionChildDescription[] getDomCollectionChildDescriptions(final AnActionEvent e) {
    final DomModelTreeView view = getTreeView(e);

    SimpleNode node = view.getTree().getSelectedNode();
    if (node instanceof BaseDomElementNode) {
      List<DomCollectionChildDescription> consolidated = ((BaseDomElementNode)node).getConsolidatedChildrenDescriptions();
      if (consolidated.size() > 0) {
        return consolidated.toArray(DomCollectionChildDescription.EMPTY_ARRAY);
      }
    }

    final DomElementsGroupNode groupNode = getDomElementsGroupNode(view);

    return groupNode == null
           ? DomCollectionChildDescription.EMPTY_ARRAY
           : new DomCollectionChildDescription[]{groupNode.getChildDescription()};
  }

  protected DomElement getParentDomElement(final AnActionEvent e) {
    final DomModelTreeView view = getTreeView(e);
    SimpleNode node = view.getTree().getSelectedNode();
    if (node instanceof BaseDomElementNode) {
      if (((BaseDomElementNode)node).getConsolidatedChildrenDescriptions().size() > 0) {
        return ((BaseDomElementNode)node).getDomElement();
      }
    }
    final DomElementsGroupNode groupNode = getDomElementsGroupNode(view);

    return groupNode == null ? null : groupNode.getDomElement();
  }

  protected JComponent getComponent(AnActionEvent e) {
    return getTreeView(e);
  }

  protected boolean showAsPopup() {
    return true;
  }

  protected String getActionText(final AnActionEvent e) {
    String text = ApplicationBundle.message("action.add");
    if (e.getPresentation().isEnabled()) {
      final DomElementsGroupNode selectedNode = getDomElementsGroupNode(getTreeView(e));
      if (selectedNode != null) {
        final Type type = selectedNode.getChildDescription().getType();
        text += " " + ElementPresentationManager.getTypeName(DomReflectionUtil.getRawType(type));
      }
    }
    return text;
  }

  @Nullable
  private static DomElementsGroupNode getDomElementsGroupNode(final DomModelTreeView treeView) {
    SimpleNode simpleNode = treeView.getTree().getSelectedNode();
    while (simpleNode != null) {
      if (simpleNode instanceof DomElementsGroupNode) return (DomElementsGroupNode)simpleNode;

      simpleNode = simpleNode.getParent();
    }
    return null;
  }


  protected DefaultAddAction createAddingAction(final AnActionEvent e,
                                                final String name,
                                                final Icon icon,
                                                final Type type,
                                                final DomCollectionChildDescription description) {

    return new DefaultAddAction(name, name, icon) {
      // we need this properties, don't remove it (shared dataContext assertion)
      private DomElement myParent = AddElementInCollectionAction.this.getParentDomElement(e);
      private DomModelTreeView myView = getTreeView(e);

      protected Type getElementType() {
        return type;
      }

      protected DomCollectionChildDescription getDomCollectionChildDescription() {
        return description;
      }

      protected DomElement getParentDomElement() {
        return myParent;
      }

      protected void afterAddition(final DomElement newElement) {
        final DomElement copy = newElement.createStableCopy();

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            myView.setSelectedDomElement(copy);
          }
        });

      }
    };
  }
}
