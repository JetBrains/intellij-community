package com.intellij.util;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.util.ui.Table;
import com.intellij.util.ui.treetable.TreeTable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class EditSourceOnDoubleClickHandler {
  public static void install(final JTree tree) {
    tree.addMouseListener(new MouseAdapter(){
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() != 2) return;
        if (tree.getPathForLocation(e.getX(), e.getY()) == null) return;
        DataContext dataContext = DataManager.getInstance().getDataContext(tree);
        Project project = (Project)dataContext.getData(DataConstants.PROJECT);
        if (project == null) return;

        Navigatable navigatable = (Navigatable)dataContext.getData(DataConstants.NAVIGATABLE);
        if (navigatable != null && navigatable.canNavigate()) {
          navigatable.navigate(true);
        }
      }
    });
  }
  public static void install(final TreeTable treeTable) {
    treeTable.addMouseListener(new MouseAdapter(){
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() != 2) return;
        if (treeTable.getTree().getPathForLocation(e.getX(), e.getY()) == null) return;
        DataContext dataContext = DataManager.getInstance().getDataContext(treeTable);
        Project project = (Project)dataContext.getData(DataConstants.PROJECT);
        if (project == null) return;
        Navigatable navigatable = (Navigatable)dataContext.getData(DataConstants.NAVIGATABLE);
        if (navigatable != null && navigatable.canNavigate()) {
          navigatable.navigate(true);
        }
      }
    });
  }

  public static void install(final Table table) {
    table.addMouseListener(new MouseAdapter(){
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() != 2) return;
        if (table.columnAtPoint(e.getPoint()) < 0) return;
        if (table.rowAtPoint(e.getPoint()) < 0) return;
        DataContext dataContext = DataManager.getInstance().getDataContext(table);
        Project project = (Project)dataContext.getData(DataConstants.PROJECT);
        if (project == null) return;
        Navigatable navigatable = (Navigatable)dataContext.getData(DataConstants.NAVIGATABLE);
        if (navigatable != null && navigatable.canNavigate()) {
          navigatable.navigate(true);
        }
      }
    });
  }

  public static void install(final JList list,
                           final Runnable whenPerformed) {
    list.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() != 2) return;
        Point point = e.getPoint();
        int index = list.locationToIndex(point);
        if (index == -1) return;
        if (!list.getCellBounds(index, index).contains(point)) return;
        DataContext dataContext = DataManager.getInstance().getDataContext(list);
        Navigatable navigatable = (Navigatable)dataContext.getData(DataConstants.NAVIGATABLE);
        if (navigatable == null || !navigatable.canNavigate()) { return; }

        navigatable.navigate(true);

        whenPerformed.run();
      }
    });
  }
}
