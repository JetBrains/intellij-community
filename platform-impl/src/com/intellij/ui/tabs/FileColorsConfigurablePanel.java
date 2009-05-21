package com.intellij.ui.tabs;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.ui.FileColorManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author spleaner
 */
public class FileColorsConfigurablePanel extends JPanel implements Disposable, ListSelectionListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.tabs.FileColorsConfigurablePanel");

  private FileColorManagerImpl myManager;
  private JCheckBox myEnabledCheckBox;
  private JCheckBox myTabsEnabledCheckBox;
  private ColorsTable myColorsTable;
  private JButton myEditButton;
  private JButton myAddButton;
  private JButton myRemoveButton;

  public FileColorsConfigurablePanel(@NotNull final FileColorManagerImpl manager) {
    setLayout(new BorderLayout());

    myManager = manager;

    final JPanel topPanel = new JPanel();
    topPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
    topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));

    myEnabledCheckBox = new JCheckBox("Enable File Colors");
    myEnabledCheckBox.setMnemonic('F');
    topPanel.add(myEnabledCheckBox);

    myTabsEnabledCheckBox = new JCheckBox("Enable Colors in Editor Tabs");
    myTabsEnabledCheckBox.setMnemonic('T');
    topPanel.add(myTabsEnabledCheckBox);

    topPanel.add(Box.createHorizontalGlue());

    add(topPanel, BorderLayout.NORTH);

    final JPanel mainPanel = new JPanel(new BorderLayout());

    myColorsTable = new ColorsTable(myManager);
    myColorsTable.getSelectionModel().addListSelectionListener(this);

    mainPanel.add(BetterJTable.createStripedJScrollPane(myColorsTable), BorderLayout.CENTER);
    mainPanel.add(buildButtons(), BorderLayout.EAST);

    add(mainPanel, BorderLayout.CENTER);
  }

  private JComponent buildButtons() {
    final JPanel result = new JPanel();
    result.setLayout(new BoxLayout(result,  BoxLayout.Y_AXIS));

    myEditButton = new JButton("Edit...");
    myEditButton.setMnemonic('E');
    myEditButton.setEnabled(false);
    result.add(myEditButton);

    myEditButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        doEditAction();
      }
    });


    myAddButton = new JButton("Add...");
    myAddButton.setMnemonic('A');
    result.add(myAddButton);

    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        doAddAction();
      }
    });

    myRemoveButton = new JButton("Remove");
    myRemoveButton.setMnemonic('R');
    myRemoveButton.setEnabled(false);
    result.add(myRemoveButton);

    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        doRemoveAction();
      }
    });

    return result;
  }

  private void doRemoveAction() {
    myColorsTable.removeSelected();
  }

  private void doAddAction() {
    doAddEditAction(false);
  }

  private void doAddEditAction(final boolean edit) {
    FileColorConfiguration configuration = null;
    if (edit) {
      final FileColorConfiguration[] selectedConfigurations = myColorsTable.getSelectedConfigurations();
      if (selectedConfigurations.length != 1) {
        return;
      }

      configuration = selectedConfigurations[0];
    }

    final FileColorConfigurationEditDialog dialog = new FileColorConfigurationEditDialog(myManager, configuration);
    dialog.show();

    if (DialogWrapper.OK_EXIT_CODE == dialog.getExitCode()) {
      if (!edit) {
        myColorsTable.addConfiguration(dialog.getConfiguration(), dialog.isShared());
      } else {
        myColorsTable.setShared(dialog.getConfiguration(), dialog.isShared());
      }
    }
  }

  private void doEditAction() {
    doAddEditAction(true);
  }

  public void dispose() {
    myManager = null;
  }

  public boolean isModified() {
    boolean modified;

    modified = myEnabledCheckBox.isSelected() != myManager.isEnabled();
    modified |= myTabsEnabledCheckBox.isSelected() != myManager.isEnabledForTabs();
    modified |= myColorsTable.isModified(myManager);

    return modified;
  }

  public void apply() {
    myManager.setEnabled(myEnabledCheckBox.isSelected());
    myManager.setEnabledForTabs(myTabsEnabledCheckBox.isSelected());

    myColorsTable.apply(myManager);

    UISettings.getInstance().fireUISettingsChanged();
  }

  public void reset() {
    myEnabledCheckBox.setSelected(myManager.isEnabled());
    myTabsEnabledCheckBox.setSelected(myManager.isEnabledForTabs());

    myColorsTable.reset(myManager);
  }

  public void valueChanged(ListSelectionEvent e) {
    final ListSelectionModel selectionModel = (ListSelectionModel)e.getSource();

    if (selectionModel.getMinSelectionIndex() == -1) {
      myEditButton.setEnabled(false);
      myRemoveButton.setEnabled(false);
      return;
    }

    if (selectionModel.getMinSelectionIndex() == selectionModel.getMaxSelectionIndex()) {
      myEditButton.setEnabled(true);
    } else {
      myEditButton.setEnabled(false);
    }

    myRemoveButton.setEnabled(true);
  }

  private static class ColorsTable extends BetterJTable {
    public ColorsTable(final FileColorManagerImpl manager) {
      super(new TableModelAdapter(manager.getModel()));

      final TableColumnModel columnModel = getColumnModel();

      final Project project = manager.getProject();

      final TableColumn pathColumn = columnModel.getColumn(0);
      pathColumn.setCellRenderer(new PathCellRenderer(project.getBaseDir().getPath()));
      pathColumn.setMinWidth(300);

      final TableColumn colorColumn = columnModel.getColumn(1);
      colorColumn.setCellRenderer(new ColorCellRenderer(manager));
      colorColumn.setMinWidth(100);

      columnModel.getColumn(2).setCellRenderer(new ShareCellRenderer());
    }

    public boolean isModified(FileColorManagerImpl manager) {
      final TableModel tableModel = getModel();
      if (tableModel instanceof TableModelAdapter) {
        return ((TableModelAdapter)tableModel).isModified(manager);
      }

      return false;
    }

    public void apply(FileColorManagerImpl manager) {
      final TableModel tableModel = getModel();
      if (tableModel instanceof TableModelAdapter) {
        ((TableModelAdapter)tableModel).apply(manager);
      }
    }

    public void reset(FileColorManagerImpl manager) {
      final TableModel tableModel = getModel();
      if (tableModel instanceof TableModelAdapter) {
        ((TableModelAdapter)tableModel).reset(manager);
      }
    }

    public FileColorConfiguration[] getSelectedConfigurations() {
      final int[] rows = getSelectedRows();
      if (rows.length == 0) {
        return new FileColorConfiguration[0];
      }

      final TableModel tableModel = getModel();
      if (tableModel instanceof TableModelAdapter) {
        final TableModelAdapter adapter = (TableModelAdapter)tableModel;

        final List<FileColorConfiguration> result = new ArrayList<FileColorConfiguration>();
        for (final int row : rows) {
          final Object o = adapter.getValueAt(row, 0);
          if (o instanceof FileColorConfiguration) {
            result.add((FileColorConfiguration) o);
          }
        }

        return result.toArray(new FileColorConfiguration[result.size()]);
      }

      return new FileColorConfiguration[0];
    }

    public void removeSelected() {
      final TableModel model = getModel();
      if (model instanceof TableModelAdapter) {
        final FileColorConfiguration[] configurations = getSelectedConfigurations();
        for (final FileColorConfiguration configuration : configurations) {
          ((TableModelAdapter)model).remove(configuration);
        }
      }
    }

    public void addConfiguration(final FileColorConfiguration configuration, final boolean shared) {
      final TableModel tableModel = getModel();
      if (tableModel instanceof TableModelAdapter) {
        final TableModelAdapter model = (TableModelAdapter)tableModel;
        model.addConfiguration(configuration, shared);
      }
    }

    public void setShared(FileColorConfiguration configuration, boolean shared) {
      final TableModel tableModel = getModel();
      if (tableModel instanceof TableModelAdapter) {
        final TableModelAdapter model = (TableModelAdapter)tableModel;
        model.setShared(configuration, shared);
      }
    }
  }

  private static class ColorCellRenderer extends PathCellRenderer {
    private Color myColor;
    private FileColorManager myManager;

    private ColorCellRenderer(final FileColorManager manager) {
      setOpaque(true);

      myManager = manager;

      setIcon(new EmptyIcon(16));
    }

    private void setIconColor(final Color color) {
      myColor = color;
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (!(value instanceof FileColorConfiguration)) {
        return this;
      }

      preinit(table, isSelected, hasFocus);

      final FileColorConfiguration configuration = (FileColorConfiguration)value;
      setIconColor(myManager.getColor(configuration.getColorName()));
      setText(configuration.getColorName());

      return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);

      if (myColor != null) {
        final Icon icon = getIcon();
        final int width = icon.getIconWidth();
        final int height = icon.getIconHeight();

        final Color old = g.getColor();

        g.setColor(myColor);
        g.fillRect(0, 0, width, height);

        g.setColor(old);
      }
    }
  }

  private static class ShareCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

      Object v = value;
      if (value instanceof FileColorConfiguration) {
        final TableModel tableModel = table.getModel();
        if (tableModel instanceof TableModelAdapter) {
          v = ((TableModelAdapter)tableModel).isShared((FileColorConfiguration) value);
        }
      }

      return super.getTableCellRendererComponent(table, v, isSelected, hasFocus, row, column);
    }
  }

  private static class PathCellRenderer extends JLabel implements TableCellRenderer {
    private static final Border NO_FOCUS_BORDER = BorderFactory.createEmptyBorder(1, 1, 1, 1);
    private String myProjectRoot;

    private PathCellRenderer() {
      setOpaque(true);
    }

    private PathCellRenderer(@NotNull final String projectRoot) {
      this();

      myProjectRoot = projectRoot;
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (!(value instanceof FileColorConfiguration)) {
        return this;
      }

      preinit(table, isSelected, hasFocus);

      final FileColorConfiguration configuration = (FileColorConfiguration)value;
      final String path = configuration.getPath();

      final int offset = StringUtil.commonPrefixLength(myProjectRoot, path);

      setText(String.format("%s", path.substring(offset)));
      return this;
    }

    protected void preinit(JTable table, boolean isSelected, boolean hasFocus) {
      setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
      setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());

      setBorder(hasFocus ? (UIManager.getBorder("Table.focusCellHighlightBorder"))
                         : NO_FOCUS_BORDER);
    }
  }

  private static class TableModelAdapter extends AbstractTableModel {
    private FileColorsModel myModel;

    private TableModelAdapter(final FileColorsModel model) {
      try {
        myModel = model.clone();
      }
      catch (CloneNotSupportedException e) {
        LOG.error(e);
      }
    }

    public void remove(@NotNull final FileColorConfiguration configuration) {
      myModel.remove(configuration);
      fireTableDataChanged();
    }

    @Override
    public String getColumnName(int column) {
      switch (column) {
        case 0:
          return "Path (relative to project root)";
        case 1:
          return "Color";
        default:
          return "Share";
      }
    }

    public int getRowCount() {
      return myModel.getConfigurations().size();
    }

    public int getColumnCount() {
      return 3;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      return myModel.getConfigurations().get(rowIndex);
    }

    public void apply(@NotNull final FileColorManagerImpl manager) {
      manager.getModel().updateFrom(myModel);
      fireTableDataChanged();
    }

    public void reset(@NotNull final FileColorManagerImpl manager) {
      myModel.updateFrom(manager.getModel());
      fireTableDataChanged();
    }

    public boolean isModified(@NotNull final FileColorManagerImpl manager) {
      return !myModel.equals(manager.getModel());
    }

    public void addConfiguration(FileColorConfiguration configuration, boolean shared) {
      myModel.add(configuration);
      myModel.setShared(configuration, shared);
      fireTableDataChanged();
    }

    public void setShared(FileColorConfiguration configuration, boolean shared) {
      myModel.setShared(configuration, shared);
      fireTableDataChanged();
    }

    public boolean isShared(FileColorConfiguration configuration) {
      return myModel.isShared(configuration);
    }
  }
}
