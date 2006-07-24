/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.ex.QuickFixAction;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 11-Jan-2006
 */
class InvokeQuickFixAction extends AnAction {
  private InspectionResultsView myView;
  private RelativePoint myPoint;

  public InvokeQuickFixAction(final InspectionResultsView view) {
    super(InspectionsBundle.message("inspection.action.apply.quickfix"), InspectionsBundle.message("inspection.action.apply.quickfix.description"), IconLoader.getIcon("/actions/createFromUsage.png"));
    myView = view;

    registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS).getShortcutSet(),
                              myView.getTree());
  }

  public void update(AnActionEvent e) {
    if (!myView.isSingleToolInSelection()) {
      e.getPresentation().setEnabled(false);
      return;
    }

    //noinspection ConstantConditions
    final @NotNull InspectionTool tool = myView.getTree().getSelectedTool();
    List<RefEntity> selectedElements = new ArrayList<RefEntity>();
    final TreePath[] selectionPaths = myView.getTree().getSelectionPaths();
    for (TreePath path : selectionPaths) {
      InspectionResultsView.traverseRefElements((InspectionTreeNode)path.getLastPathComponent(), selectedElements);
    }
    final QuickFixAction[] quickFixes = tool.getQuickFixes(selectedElements.toArray(new RefEntity[selectedElements.size()]));
    if (quickFixes == null || quickFixes.length == 0) {
      e.getPresentation().setEnabled(false);
      return;
    }

    ActionGroup fixes = new ActionGroup() {
      public AnAction[] getChildren(@Nullable AnActionEvent e) {
        List<QuickFixAction> children = new ArrayList<QuickFixAction>();
        for (QuickFixAction fix : quickFixes) {
          if (fix != null) {
            children.add(fix);
          }
        }
        return children.toArray(new AnAction[children.size()]);
      }
    };

    e.getPresentation().setEnabled(!ActionGroupUtil.isGroupEmpty(fixes, e));
  }

  public void actionPerformed(AnActionEvent e) {
    final InspectionTool tool = myView.getTree().getSelectedTool();
    assert tool != null;
    List<RefEntity> selectedElements = new ArrayList<RefEntity>();
    final TreePath[] selectionPaths = myView.getTree().getSelectionPaths();
    for (TreePath path : selectionPaths) {
      InspectionResultsView.traverseRefElements((InspectionTreeNode)path.getLastPathComponent(), selectedElements);
    }
    final QuickFixAction[] quickFixes = tool.getQuickFixes(selectedElements.toArray(new RefElement[selectedElements.size()]));
    ActionGroup fixes = new ActionGroup() {
      public AnAction[] getChildren(@Nullable AnActionEvent e) {
        List<QuickFixAction> children = new ArrayList<QuickFixAction>();
        for (QuickFixAction fix : quickFixes) {
          if (fix != null) {
            children.add(fix);
          }
        }
        return children.toArray(new AnAction[children.size()]);
      }
    };

    DataContext dataContext = e.getDataContext();
    final ListPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(InspectionsBundle.message("inspection.tree.popup.title"),
                              fixes,
                              dataContext,
                              JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                              false);

    popup.show(myPoint);
  }

  public void setupPopupCoordinates(RelativePoint point) {
    myPoint = point;
  }
}
