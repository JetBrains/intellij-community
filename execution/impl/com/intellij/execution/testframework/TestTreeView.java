/*
 * User: anna
 * Date: 25-May-2007
 */
package com.intellij.execution.testframework;

import com.intellij.execution.junit2.ui.PoolOfTestIcons;
import com.intellij.execution.junit2.ui.TestsUIUtil;
import com.intellij.execution.junit2.ui.actions.ViewAssertEqualsDiffAction;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.plaf.TreeUI;
import javax.swing.tree.*;

public abstract class TestTreeView extends Tree implements DataProvider {
  private TestFrameworkRunningModel myModel;

  protected abstract TreeCellRenderer getRenderer(TestConsoleProperties properties);

  protected abstract AbstractTestProxy getSelectedTest(@NotNull TreePath selectionPath);

  @Nullable
  public AbstractTestProxy getSelectedTest() {
    final TreePath selectionPath = getSelectionPath();
    return selectionPath != null ? getSelectedTest(selectionPath) : null;
  }

  public void attachToModel(final TestFrameworkRunningModel model) {
    setModel(new DefaultTreeModel(new DefaultMutableTreeNode(model.getRoot())));
    getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myModel = model;
    myModel.addListener(new TestFrameworkRunningModel.ModelListener() {
      public void onDispose() {
        setModel(null);
        myModel = null;
      }
    });
    installHandlers();
    setCellRenderer(getRenderer(myModel.getProperties()));
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
    final AbstractTestProxy testProxy = getSelectedTest(selectionPath);
    if (testProxy == null) return null;
    return TestsUIUtil.getData(testProxy, dataId, myModel);
  }

  private void installHandlers() {
    EditSourceOnDoubleClickHandler.install(this);
    new TreeSpeedSearch(this, new Convertor<TreePath, String>() {
      public String convert(final TreePath path) {
        final AbstractTestProxy testProxy = getSelectedTest(path);
        if (testProxy == null) return null;
        return testProxy.getName();
      }
    });
    TreeToolTipHandler.install(this);
    TreeUtil.installActions(this);
    PopupHandler.installPopupHandler(this, IdeActions.GROUP_TESTTREE_POPUP, ActionPlaces.TESTTREE_VIEW_POPUP);
    ViewAssertEqualsDiffAction.registerShortcut(this);
  }
}