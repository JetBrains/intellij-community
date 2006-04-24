package com.intellij.execution.junit2.ui;

import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.ui.actions.ViewAssertEqualsDiffAction;
import com.intellij.execution.junit2.ui.model.JUnitAdapter;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.ui.PopupHandler;
import com.intellij.util.ui.Tree;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.plaf.TreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

public class TestTreeView extends Tree implements DataProvider {
  private JUnitRunningModel myModel;

  public void attachToModel(final JUnitRunningModel model) {
    setModel(new DefaultTreeModel(new DefaultMutableTreeNode(model.getRoot())));
    getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myModel = model;
    myModel.addListener(new JUnitAdapter() {
      public void doDispose() {
        setModel(null);
        myModel = null;
      }
    });
    installHandlers();
    setCellRenderer(new TreeTestRenderer(model.getProperties()));
  }

  public void setUI(final TreeUI ui) {
    super.setUI(ui);
    final int fontHeight = getFontMetrics(getFont()).getHeight();
    final int iconHeight = PoolOfTestIcons.PASSED_ICON.getIconHeight();
    setRowHeight(Math.max(fontHeight, iconHeight) + 2);
    setLargeModel(true);
  }

  public Object getData(final String dataId) {
    final TreePath selectionPath = getSelectionPath();
    if (selectionPath == null) return null;
    final TestProxy testProxy = TestProxyClient.from(selectionPath.getLastPathComponent());
    if (testProxy == null) return null;
    return TestsUIUtil.getData(testProxy, dataId, myModel);
  }

  private void installHandlers() {
    EditSourceOnDoubleClickHandler.install(this);
    new TreeSpeedSearch(this, new Convertor<TreePath, String>() {
      public String convert(final TreePath path) {
        final TestProxy testProxy = TestProxyClient.from(path.getLastPathComponent());
        if (testProxy == null) return null;
        return testProxy.getInfo().getName();
      }
    });
    TreeToolTipHandler.install(this);
    TreeUtil.installActions(this);
    installTestTreePopupHandler(this);
    ViewAssertEqualsDiffAction.registerShortcut(this);
  }

  public String convertValueToText(final Object value, final boolean selected,
                                   final boolean expanded, final boolean leaf, final int row,
                                   final boolean hasFocus) {
    return Formatters.printTest(TestProxyClient.from(value));
  }

  public static void installTestTreePopupHandler(final JComponent component) {
    PopupHandler.installPopupHandler(component,
                        IdeActions.GROUP_TESTTREE_POPUP,
                        ActionPlaces.TESTTREE_VIEW_POPUP);
  }
}
