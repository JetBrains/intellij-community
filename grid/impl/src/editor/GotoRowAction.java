package com.intellij.database.editor;

import com.intellij.database.DataGridBundle;
import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Gregory.Shrago
 */
public class GotoRowAction extends AnAction implements DumbAware {
  public static final String GO_TO_ROW_EXECUTOR_KEY = "GoToRowExecutor";

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  public GotoRowAction() {
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DataGrid dataGrid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (dataGrid != null) {
      showGoToDialog(dataGrid);
    }
  }

  public static void showGoToDialog(DataGrid dataGrid) {
    GoToRowHelper executor =
      ObjectUtils.tryCast(dataGrid.getResultView().getComponent().getClientProperty(GO_TO_ROW_EXECUTOR_KEY), GoToRowHelper.class);
    if (executor == null) return;
    GotoRowDialog dialog = new GotoRowDialog(dataGrid, executor);
    dialog.show();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final DataGrid resultPanel = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    JComponent component = resultPanel == null ? null : resultPanel.getResultView().getComponent();
    final boolean enabled = component != null &&
                            ObjectUtils.tryCast(component.getClientProperty(GO_TO_ROW_EXECUTOR_KEY), GoToRowHelper.class) != null;

    e.getPresentation().setEnabledAndVisible(enabled);
  }

  private static class GotoRowDialog extends DialogWrapper {

    private JTextField myField;
    private final GoToRowHelper myExecutor;

    GotoRowDialog(DataGrid dataGrid, GoToRowHelper executor) {
      super(dataGrid.getPreferredFocusedComponent(), true);
      myExecutor = executor;
      setTitle(DataGridBundle.message("dialog.title.go.to.row"));
      init();
    }

    @Override
    protected void doOKAction() {
      String text = getText();
      int idx = separatorIndex(text);
      String row = idx == -1 ? text : text.substring(0, idx).trim();
      String column = idx == -1 ? "" : text.substring(idx + 1).trim();

      myExecutor.goToRow(row, column);
      super.doOKAction();
    }

    private static int separatorIndex(final String text) {
      final int colonIndex = text.indexOf(':');
      return colonIndex >= 0 ? colonIndex : text.indexOf(',');
    }

    @Override
    public @NotNull JComponent getPreferredFocusedComponent() {
      return myField;
    }

    @Override
    protected JComponent createCenterPanel() {
      return null;
    }

    private String getText() {
      return myField.getText();
    }

    @Override
    protected JComponent createNorthPanel() {
      class MyTextField extends JTextField {
        MyTextField() {
          super("");
        }

        @Override
        public @NotNull Dimension getPreferredSize() {
          Dimension d = super.getPreferredSize();
          return new Dimension(200, d.height);
        }
      }

      JPanel panel = new JPanel(new GridBagLayout());
      GridBagConstraints gbConstraints = new GridBagConstraints();

      gbConstraints.insets = JBUI.insets(4, 0, 8, 4);
      gbConstraints.fill = GridBagConstraints.VERTICAL;
      gbConstraints.weightx = 0;
      gbConstraints.weighty = 1;
      gbConstraints.anchor = GridBagConstraints.EAST;
      JLabel label = new JLabel(DataGridBundle.message("row.column"));
      panel.add(label, gbConstraints);

      gbConstraints.fill = GridBagConstraints.BOTH;
      gbConstraints.weightx = 1;
      myField = new MyTextField();
      panel.add(myField, gbConstraints);

      myField.setToolTipText(StringUtil.escapeXmlEntities(DataGridBundle.message("row.column.or.row.column")));

      return panel;
    }
  }

  public interface GoToRowHelper {
    void goToRow(@NotNull String row, @NotNull String column);
  }
}
