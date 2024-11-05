// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.registry;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.RegistryBooleanOptionDescriptor;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ExperimentalFeature;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.impl.IdeBackgroundUtil;
import com.intellij.ui.*;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Kirill Kalishev
 * @author Konstantin Bulenkov
 */
public class RegistryUi implements Disposable {
  private static final String RECENT_PROPERTIES_KEY = "RegistryRecentKeys";

  private final JBTable myTable;
  private final JTextArea myDescriptionLabel;

  private final JPanel myContent = new JPanel();

  private final RestoreDefaultsAction myRestoreDefaultsAction;
  private final MyTableModel myModel;
  private final Map<String, String> myModifiedValues = new HashMap<>();

  public RegistryUi() {
    myContent.setLayout(new BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));

    myModel = new MyTableModel();
    myTable = new JBTable(myModel);
    myTable.setShowGrid(false);
    myTable.setVisibleRowCount(15);
    myTable.setEnableAntialiasing(true);
    MyRenderer r = new MyRenderer();

    TableColumn c1 = myTable.getColumnModel().getColumn(0);
    c1.setPreferredWidth(JBUI.scale(400));
    c1.setCellRenderer(r);
    c1.setHeaderValue("Key");

    TableColumn c2 = myTable.getColumnModel().getColumn(1);
    c2.setPreferredWidth(JBUI.scale(100));
    c2.setCellRenderer(r);
    c2.setHeaderValue("Value");
    c2.setCellEditor(new MyEditor());

    TableColumn c3 = myTable.getColumnModel().getColumn(2);
    c3.setPreferredWidth(JBUI.scale(100));
    c3.setHeaderValue("Source");

    myDescriptionLabel = new JTextArea(3, 50);
    myDescriptionLabel.setMargin(JBUI.insets(2));
    myDescriptionLabel.setWrapStyleWord(true);
    myDescriptionLabel.setLineWrap(true);
    myDescriptionLabel.setEditable(false);
    myDescriptionLabel.setBackground(UIUtil.getPanelBackground());
    myDescriptionLabel.setFont(JBFont.label());

    JScrollPane label = ScrollPaneFactory.createScrollPane(myDescriptionLabel, SideBorder.NONE);
    JPanel descriptionPanel = new JPanel(new BorderLayout());
    descriptionPanel.add(label, BorderLayout.CENTER);
    descriptionPanel.setBorder(JBUI.Borders.emptyTop(8));

    myContent.add(ScrollPaneFactory.createScrollPane(myTable), BorderLayout.CENTER);
    myContent.add(descriptionPanel, BorderLayout.SOUTH);

    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(@NotNull ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;

        int viewRow = myTable.getSelectedRow();
        if (viewRow != -1) {
          int modelRow = myTable.convertRowIndexToModel(viewRow);
          RegistryValue value = myModel.getRegistryValue(modelRow);
          String description = value.getDescription();
          if (value.isRestartRequired()) {
            myDescriptionLabel.setText(description + "\n" + IdeBundle.message("registry.key.requires.ide.restart.note"));
          }
          else {
            myDescriptionLabel.setText(description);
          }
        } else {
          myDescriptionLabel.setText(null);
        }
      }
    });

    myRestoreDefaultsAction = new RestoreDefaultsAction();

    final DefaultActionGroup tbGroup = new DefaultActionGroup();
    tbGroup.add(new EditAction());
    tbGroup.add(new RevertAction());

    final ActionToolbar tb = ActionManager.getInstance().createActionToolbar("Registry", tbGroup, true);
    tb.setTargetComponent(myTable);

    myContent.add(tb.getComponent(), BorderLayout.NORTH);
    final TableSpeedSearch search = TableSpeedSearch.installOn(myTable);
    search.setFilteringMode(true);

    myTable.setTransferHandler(new RegistryTransferHandler(search));

    myTable.setRowSorter(new TableRowSorter<>(myTable.getModel()));
    myTable.registerKeyboardAction(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          int[] rows = myTable.getSelectedRows();
          if (rows.length == 0) return;
          for (int row : rows) {
            int modelRow = myTable.convertRowIndexToModel(row);
            RegistryValue rv = myModel.getRegistryValue(modelRow);
            if (rv.isBoolean()) {
              setValue(rv, !rv.asBoolean());
              keyChanged(rv.getKey());
              myModel.fireTableCellUpdated(modelRow, 0);
              myModel.fireTableCellUpdated(modelRow, 1);
              myModel.fireTableCellUpdated(modelRow, 2);
            }
          }
          invalidateActions();
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), JComponent.WHEN_FOCUSED);
    ScrollingUtil.ensureSelectionExists(myTable);
  }

  private final class RevertAction extends AnAction implements DumbAware {
    private RevertAction() {
      new ShadowAction(this, "EditorDelete", myTable, RegistryUi.this);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(!myTable.isEditing() && myTable.getSelectedRow() >= 0);
      e.getPresentation().setText(IdeBundle.messagePointer("action.presentation.RegistryUi.text"));
      e.getPresentation().setIcon(AllIcons.General.Reset);

      if (e.getPresentation().isEnabled()) {
        RegistryValue rv = myModel.getRegistryValue(myTable.convertRowIndexToModel(myTable.getSelectedRow()));
        e.getPresentation().setEnabled(rv.isChangedFromDefault());
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final RegistryValue rv = myModel.getRegistryValue(myTable.convertRowIndexToModel(myTable.getSelectedRow()));
      rv.resetToDefault();
      myModel.fireTableCellUpdated(myTable.getSelectedRow(), 0);
      myModel.fireTableCellUpdated(myTable.getSelectedRow(), 1);
      invalidateActions();
    }
  }

  private final class EditAction extends AnAction implements DumbAware {
    private EditAction() {
      new ShadowAction(this, IdeActions.ACTION_EDIT_SOURCE, myTable, RegistryUi.this);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(!myTable.isEditing() && myTable.getSelectedRow() >= 0);
      e.getPresentation().setText(IdeBundle.messagePointer("action.presentation.RegistryUi.text.edit"));
      e.getPresentation().setIcon(AllIcons.Actions.EditSource);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      startEditingAtSelection();
    }
  }

  private void startEditingAtSelection() {
    myTable.editCellAt(myTable.getSelectedRow(), 1);
    if (myTable.isEditing()) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myTable.getEditorComponent(), true));
    }
  }

  private static final class MyTableModel extends AbstractTableModel {
    private final List<RegistryValue> myAll;

    private MyTableModel() {
      myAll = Registry.getAll();
      for (ExperimentalFeature feature : Experiments.EP_NAME.getExtensionList()) {
        myAll.add(new ExperimentalFeatureRegistryValueWrapper(feature));
      }

      List<String> recent = getRecent();

      myAll.sort((o1, o2) -> {
        final String key1 = o1.getKey();
        boolean changed1 = o1.isChangedFromDefault();
        boolean changed2 = o2.isChangedFromDefault();
        if (changed1 && !changed2) return -1;
        if (!changed1 && changed2) return 1;

        final String key2 = o2.getKey();
        final int i1 = recent.indexOf(key1);
        final int i2 = recent.indexOf(key2);
        final boolean c1 = i1 != -1;
        final boolean c2 = i2 != -1;
        if (c1 && !c2) return -1;
        if (!c1 && c2) return 1;
        if (c1) return i1 - i2;
        return key1.compareToIgnoreCase(key2);
      });
    }

    public void fireChanged() {
      fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
      return myAll.size();
    }

    @Override
    public int getColumnCount() {
      return 3;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      RegistryValue value = getRegistryValue(rowIndex);
      return switch (columnIndex) {
        case 0 -> value.getKey();
        case 1 -> value.asString();
        case 2 -> {
          RegistryValueSource source = value.getSource();
          yield source != null ? source.name() : "";
        }
        default -> value;
      };
    }

    private RegistryValue getRegistryValue(final int rowIndex) {
      return myAll.get(rowIndex);
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == 1;
    }
  }

  private static List<String> getRecent() {
    String value = PropertiesComponent.getInstance().getValue(RECENT_PROPERTIES_KEY);
    return StringUtil.isEmpty(value) ? new ArrayList<>(0) : StringUtil.split(value, "=");
  }

  private static void keyChanged(String key) {
    final List<String> recent = getRecent();
    recent.remove(key);
    recent.add(0, key);
    PropertiesComponent.getInstance().setValue(RECENT_PROPERTIES_KEY, StringUtil.join(recent, "="), "");
  }

  public boolean show() {
    DialogWrapper dialog = new DialogWrapper(true) {
      {
        setTitle(LangBundle.message("dialog.title.registry"));
        setModal(true);
        init();
        invalidateActions();
      }

      private AbstractAction myCloseAction;

      @Override
      protected @Nullable JComponent createNorthPanel() {
        if (ApplicationManager.getApplication().isInternal()) {
          return null;
        }
        String warning = new HtmlBuilder().append(
          HtmlChunk.tag("b").addText(
            IdeBundle.message("registry.change.warning", ApplicationNamesInfo.getInstance().getFullProductName())
          )
        ).wrapWithHtmlBody().toString();
        JLabel warningLabel = new JLabel(warning);
        warningLabel.setIcon(UIUtil.getWarningIcon());
        warningLabel.setForeground(JBColor.RED);
        return warningLabel;
      }

      @Override
      protected JComponent createCenterPanel() {
        return myContent;
      }

      @Override
      protected void dispose() {
        super.dispose();
        Disposer.dispose(RegistryUi.this);
      }

      @Override
      protected String getDimensionServiceKey() {
        return "Registry";
      }

      @Override
      public JComponent getPreferredFocusedComponent() {
        return myTable;
      }

      @Override
      protected Action @NotNull [] createActions() {
        return new Action[]{myRestoreDefaultsAction, myCloseAction};
      }

      @Override
      protected void createDefaultActions() {
        super.createDefaultActions();
        myCloseAction = new AbstractAction(IdeBundle.message("registry.close.action.text")) {
          @Override
          public void actionPerformed(@NotNull ActionEvent e) {
            processClose();
            doOKAction();
          }
        };
        myCloseAction.putValue(DialogWrapper.DEFAULT_ACTION, true);
      }

      @Override
      public void doCancelAction() {
        final TableCellEditor cellEditor = myTable.getCellEditor();
        if (cellEditor != null) {
          cellEditor.stopCellEditing();
        }
        processClose();
        super.doCancelAction();
      }

      @Override
      public @NotNull Dimension getInitialSize() {
        return new JBDimension(800, 600, false);
      }
    };

    return dialog.showAndGet();
  }

  private void processClose() {
    if (!myModifiedValues.isEmpty()) {
      IdeBackgroundUtil.repaintAllWindows();
    }
    if (ContainerUtil.find(myModifiedValues.keySet(), o -> Registry.get(o).isRestartRequired()) != null) {
      RegistryBooleanOptionDescriptor.suggestRestart(myContent);
    }
  }

  private void setValue(@NotNull RegistryValue registryValue, boolean value) {
    setValue(registryValue, Boolean.toString(value));
  }

  private void setValue(@NotNull RegistryValue registryValue, @NotNull String value) {
    String key = registryValue.getKey();
    if (!myModifiedValues.containsKey(key)) {
      // store previous value that represent an initial value for this dialog
      myModifiedValues.put(key, registryValue.asString());
    }
    else if (value.equals(myModifiedValues.get(key))) {
      // remove stored value if it is equals to the new value
      myModifiedValues.remove(key);
    }
    registryValue.setValue(value, RegistryValueSource.USER);
  }

  private void restoreDefaults() {
    String message = LangBundle.message("dialog.message.are.you.sure.you.want.to.revert.registry.settings.to.default.values");
    int r = Messages.showYesNoDialog(myContent, message, LangBundle.message("dialog.title.revert.to.defaults"), Messages.getQuestionIcon());
    if (r == Messages.YES) {
      Registry.getInstance().restoreDefaults();
      myModel.fireChanged();
      invalidateActions();
    }
  }

  private void invalidateActions() {
    myRestoreDefaultsAction.setEnabled(!Registry.getInstance().isInDefaultState());
  }

  @Override
  public void dispose() { }

  private static @NotNull List<String> getOptions(@NotNull RegistryValue value) {
    List<String> options = new ArrayList<>(value.asOptions());
    options.replaceAll(s -> Strings.trimEnd(s, "*"));
    return options;
  }

  private static final class MyRenderer implements TableCellRenderer {
    private final JLabel myLabel = new JLabel();
    private final SimpleColoredComponent myComponent = new SimpleColoredComponent();

    @Override
    public @NotNull Component getTableCellRendererComponent(@NotNull JTable table,
                                                            Object value,
                                                            boolean isSelected,
                                                            boolean hasFocus,
                                                            int row,
                                                            int column) {
      int modelRow = table.convertRowIndexToModel(row);
      RegistryValue v = ((MyTableModel)table.getModel()).getRegistryValue(modelRow);

      Color bg = isSelected ? table.getSelectionBackground() : table.getBackground();

      if (v != null) {
        switch (column) {
          case 0 -> {
            myComponent.clear();
            myComponent.append(v.getKey(), getAttributes(v, isSelected));
            myComponent.setBackground(bg);

            if (v.isRestartRequired()) {
              myComponent.setIconOnTheRight(true);
              myComponent.setIcon(AllIcons.General.Warning);
              myComponent.setIconOpaque(false);
              myComponent.setTransparentIconBackground(true);
            }

            SpeedSearchUtil.applySpeedSearchHighlighting(table, myComponent, true, hasFocus);
            return myComponent;
          }
          case 1 -> {
            if (v.asColor(null) != null) {
              myLabel.setText(null);
              myLabel.setToolTipText(v.asString());
              myLabel.setIcon(createColoredIcon(v.asColor(null)));
              myLabel.setHorizontalAlignment(SwingConstants.LEFT);
            }
            else if (v.isBoolean()) {
              final JCheckBox box = new JCheckBox();
              box.setSelected(v.asBoolean());
              box.setBackground(bg);
              return box;
            }
            else if (v.isMultiValue()) {
              List<String> options = getOptions(v);
              ComboBox<String> combo = new ComboBox<>(ArrayUtil.toStringArray(options));
              combo.setSelectedItem(v.getSelectedOption());
              return combo;
            }
            else {
              myComponent.clear();
              myComponent.setBackground(bg);
              myComponent.append(v.asString(), getAttributes(v, isSelected));
              if (v.isChangedFromDefault()) {
                myComponent.append(" [" + Registry.getInstance().getBundleValueOrNull(v.getKey()) + "]",
                                   SimpleTextAttributes.GRAYED_ATTRIBUTES);
              }
              SpeedSearchUtil.applySpeedSearchHighlighting(table, myComponent, true, hasFocus);
              return myComponent;
            }
          }
        }

        myLabel.setOpaque(true);
        myLabel.setBackground(bg);
      }

      return myLabel;
    }

    private static @NotNull SimpleTextAttributes getAttributes(RegistryValue value, boolean isSelected) {
      boolean changedFromDefault = value.isChangedFromDefault();
      if (isSelected) {
        return new SimpleTextAttributes(changedFromDefault ? SimpleTextAttributes.STYLE_BOLD : SimpleTextAttributes.STYLE_PLAIN,
                                        NamedColorUtil.getListSelectionForeground(true));
      }

      if (changedFromDefault) {
        return new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.blue);
      }
      return SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }
  }

  private static final Map<Color, Icon> icons_cache = new HashMap<>();
  private static Icon createColoredIcon(Color color) {
    Icon icon = icons_cache.get(color);
    if (icon != null) return icon;
    final BufferedImage image = GraphicsEnvironment.getLocalGraphicsEnvironment()
      .getDefaultScreenDevice().getDefaultConfiguration()
      .createCompatibleImage(16, 16, Transparency.TRANSLUCENT);
    final Graphics g = image.getGraphics();
    g.setColor(color);
    g.fillRect(0, 0, 16, 16);
    g.dispose();
    icon = new ImageIcon(image);
    icons_cache.put(color, icon);
    return icon;
  }

  private final class MyEditor extends AbstractCellEditor implements TableCellEditor {

    private final JTextField myField = new JTextField();
    private final JCheckBox myCheckBox = new JCheckBox();
    private ComboBox<String> myComboBox;
    private RegistryValue myValue;

    {
      myCheckBox.addActionListener(e -> stopCellEditing());
    }

    @Override
    public @Nullable Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      int modelRow = table.convertRowIndexToModel(row);
      myValue = ((MyTableModel)table.getModel()).getRegistryValue(modelRow);
      if (myValue.asColor(null) != null) {
        final Color color = ColorChooserService.getInstance().showDialog(table, IdeBundle.message("dialog.title.choose.color"), myValue.asColor(JBColor.WHITE));
        if (color != null) {
          setValue(myValue, color.getRed() + "," + color.getGreen() + "," + color.getBlue());
          keyChanged(myValue.getKey());
        }
        return null;
      }
      else if (myValue.isBoolean()) {
        myCheckBox.setSelected(myValue.asBoolean());
        myCheckBox.setBackground(table.getBackground());
        return myCheckBox;
      }
      else if (myValue.isMultiValue()) {
        myComboBox = new ComboBox<>(ArrayUtil.toStringArray(getOptions(myValue)));
        myComboBox.setSelectedItem(myValue.getSelectedOption());
        return myComboBox;
      }
      else {
        myField.setText(myValue.asString());
        myField.setBorder(null);
        myField.selectAll();
        return myField;
      }
    }

    @Override
    public boolean stopCellEditing() {
      if (myValue != null) {
        if (myValue.isBoolean()) {
          setValue(myValue, myCheckBox.isSelected());
        }
        else if (myValue.isMultiValue()) {
          String selected = (String)myComboBox.getSelectedItem();
          myValue.setSelectedOption(selected, RegistryValueSource.USER);
        }
        else {
          setValue(myValue, myField.getText().trim());
        }
        keyChanged(myValue.getKey());
      }
      invalidateActions();
      return super.stopCellEditing();
    }

    @Override
    public Object getCellEditorValue() {
      return myValue;
    }
  }

  private final class RestoreDefaultsAction extends AbstractAction {
    RestoreDefaultsAction() {
      super(IdeBundle.message("registry.restore.defaults.action.text"));
    }

    @Override
    public void actionPerformed(@NotNull ActionEvent e) {
      restoreDefaults();
    }
  }

  private static class RegistryTransferHandler extends TransferHandler {
    private final @NotNull TableSpeedSearch mySearch;

    private RegistryTransferHandler(@NotNull TableSpeedSearch search) {
      mySearch = search;
    }

    @Override
    public boolean importData(@NotNull TransferSupport support) {
      String pastedText = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
      if (pastedText == null || mySearch.isPopupActive()) {
        return false;
      }
      mySearch.showPopup(pastedText);
      return true;
    }

    @Override
    public int getSourceActions(JComponent c) {
      return COPY;
    }

    @Override
    protected @Nullable Transferable createTransferable(JComponent c) {
      JTable table = (JTable)c;

      int[] selectedRows = table.getSelectedRows();
      if (selectedRows == null || selectedRows.length == 0) {
        return null;
      }

      String htmlText = buildHtmlText(table, selectedRows);
      String plainText = buildPlainText(table, selectedRows);

      return new TextTransferable(htmlText, plainText);
    }

    private static @NotNull String buildPlainText(@NotNull JTable table, int[] rows) {
      StringBuilder stringBuilder = new StringBuilder();
      int lastRow = rows[rows.length - 1];

      int columnCount = table.getColumnCount();
      for (int row : rows) {
        for (int col = 0; col < columnCount; col++) {
          String text = getItemAsString(table, row, col);
          stringBuilder.append(text);
          if (col + 1 != columnCount) {
            stringBuilder.append('\t');
          }
        }
        if (lastRow != row) {
          stringBuilder.append('\n');
        }
      }

      return stringBuilder.toString();
    }

    private static @NotNull String buildHtmlText(@NotNull JTable table, int[] rows) {
      HtmlBuilder builder = new HtmlBuilder();
      int columnCount = table.getColumnCount();
      for (int row : rows) {
        HtmlBuilder rowItem = new HtmlBuilder();
        for (int col = 0; col < columnCount; col++) {
          @NonNls String text = getItemAsString(table, row, col);
          HtmlChunk.Element item = new HtmlBuilder().append(text).wrapWith("td");
          rowItem.append(item);
        }
        builder.append(rowItem.wrapWith("tr"));
      }
      return builder.wrapWith("table").wrapWith("body").wrapWith("html").toString();
    }

    private static String getItemAsString(@NonNls JTable table, int row, int col) {
      Object obj = table.getValueAt(row, col);
      return (obj == null) ? "" : obj.toString();
    }
  }
}
