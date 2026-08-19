// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.settings.mappings;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.json.JsonBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.NlsContexts.PopupContent;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.table.TableView;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.jsonSchema.JsonMappingKind;
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import static com.jetbrains.jsonSchema.remote.JsonFileResolver.isAbsoluteUrl;
import static com.jetbrains.jsonSchema.remote.JsonFileResolver.isHttpPath;

public final class JsonSchemaMappingsView implements Disposable {
  private static final String ADD_SCHEMA_MAPPING = "settings.json.schema.add.mapping";
  private static final String EDIT_SCHEMA_MAPPING = "settings.json.schema.edit.mapping";
  private static final String REMOVE_SCHEMA_MAPPING = "settings.json.schema.remove.mapping";
  private final TreeUpdater myTreeUpdater;
  private final @NotNull BiConsumer<@NotNull String, @NotNull Boolean> mySchemaPathChangedCallback;
  private TableView<UserDefinedJsonSchemaConfiguration.Item> myTableView;
  private JsonSchemaMappingsViewUi ui;
  private Project myProject;
  private boolean myInitialized;

  public JsonSchemaMappingsView(Project project,
                                TreeUpdater treeUpdater,
                                @NotNull BiConsumer<@NotNull String, @NotNull Boolean> schemaPathChangedCallback) {
    myTreeUpdater = treeUpdater;
    mySchemaPathChangedCallback = schemaPathChangedCallback;
    createUI(project);
  }

  private void createUI(final Project project) {
    myProject = project;
    MyAddActionButtonRunnable addActionButtonRunnable = new MyAddActionButtonRunnable();

    myTableView = new JsonMappingsTableView(addActionButtonRunnable);
    myTableView.setShowGrid(false);
    myTableView.getTableHeader().setVisible(false);
    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myTableView);
    final MyEditActionButtonRunnableImpl editAction = new MyEditActionButtonRunnableImpl();
    decorator.setRemoveAction(new MyRemoveActionButtonRunnable())
             .setRemoveActionName(REMOVE_SCHEMA_MAPPING)
             .setAddAction(addActionButtonRunnable)
             .setAddActionName(JsonBundle.message(ADD_SCHEMA_MAPPING))
             .setEditAction(editAction)
             .setEditActionName(JsonBundle.message(EDIT_SCHEMA_MAPPING))
             .disableUpDownActions();

    ui = new JsonSchemaMappingsViewUi(project, mySchemaPathChangedCallback, decorator, this);
    attachNavigateToSchema();
  }

  @Override
  public void dispose() {
  }

  public void setError(@PopupContent String text, boolean showWarning) {
    ui.setError(text, showWarning);
  }

  private void attachNavigateToSchema() {
    DumbAwareAction.create(_ -> {
      String pathToSchema = ui.schemaField.getText();
      if (StringUtil.isEmptyOrSpaces(pathToSchema) || isHttpPath(pathToSchema)) return;
      VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(pathToSchema));
      if (virtualFile == null) {
        BalloonBuilder balloonBuilder = JBPopupFactory.getInstance()
          .createHtmlTextBalloonBuilder(JsonBundle.message("json.schema.file.not.found"), UIUtil.getBalloonErrorIcon(), MessageType.ERROR.getPopupBackground(), null);
        Balloon balloon = balloonBuilder.setFadeoutTime(TimeUnit.SECONDS.toMillis(3)).createBalloon();
        balloon.showInCenterOf(ui.schemaField);
        return;
      }
      PsiNavigationSupport.getInstance().createNavigatable(myProject, virtualFile, -1).navigate(true);
    }).registerCustomShortcutSet(CommonShortcuts.getEditSource(), ui.schemaField);
  }

  public List<UserDefinedJsonSchemaConfiguration.Item> getData() {
    return ContainerUtil
      .filter(myTableView.getListTableModel().getItems(), i -> i.mappingKind == JsonMappingKind.Directory || !StringUtil.isEmpty(i.path));
  }

  public void setItems(String schemaFilePath,
                       JsonSchemaVersion version,
                       final List<UserDefinedJsonSchemaConfiguration.Item> data) {
    myInitialized = true;
    ui.schemaField.setText(schemaFilePath);
    ui.schemaVersionComboBox.setSelectedItem(version);
    myTableView.setModelAndUpdateColumns(
      new ListTableModel<>(createColumns(), new ArrayList<>(data)));
  }

  public boolean isInitialized() {
    return myInitialized;
  }

  public JsonSchemaVersion getSchemaVersion() {
    return (JsonSchemaVersion)ui.schemaVersionComboBox.getSelectedItem();
  }

  public String getSchemaSubPath() {
    String schemaFieldText = ui.schemaField.getText();
    if (isAbsoluteUrl(schemaFieldText)) return schemaFieldText;
    return FileUtil.toSystemDependentName(JsonSchemaInfo.getRelativePath(myProject, schemaFieldText));
  }

  private ColumnInfo[] createColumns() {
    return new ColumnInfo[] { new MappingItemColumnInfo() };
  }

  public JComponent getComponent() {
    return ui.panel;
  }

  private final class MappingItemColumnInfo extends ColumnInfo<UserDefinedJsonSchemaConfiguration.Item, String> {
    MappingItemColumnInfo() {super("");}

    @Override
    public @NotNull String valueOf(UserDefinedJsonSchemaConfiguration.Item item) {
      return item.getPresentation();
    }

    @Override
    public @NotNull TableCellRenderer getRenderer(UserDefinedJsonSchemaConfiguration.Item item) {
      return new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
          JLabel label = (JLabel)super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          label.setIcon(item.mappingKind.getIcon());

          String error = item.getError();
          if (error == null) {
            return label;
          }

          JPanel panel = new JPanel();
          panel.setLayout(new BorderLayout());
          panel.add(label, BorderLayout.CENTER);
          JLabel warning = new JLabel(AllIcons.General.Warning);
          panel.setBackground(label.getBackground());
          panel.setToolTipText(error);
          panel.add(warning, BorderLayout.LINE_END);
          return panel;
        }
      };
    }

    @Override
    public @NotNull TableCellEditor getEditor(UserDefinedJsonSchemaConfiguration.Item item) {
      return new JsonMappingsTableCellEditor(item, myProject, myTreeUpdater);
    }

    @Override
    public boolean isCellEditable(UserDefinedJsonSchemaConfiguration.Item item) {
      return true;
    }
  }

  final class MyAddActionButtonRunnable implements AnActionButtonRunnable {
    MyAddActionButtonRunnable() {
      super();
    }

    @Override
    public void run(AnActionButton button) {
      RelativePoint point = button.getPreferredPopupPoint();
      JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<>(null,
                                                                           JsonMappingKind.values()) {
        @Override
        public @NotNull String getTextFor(JsonMappingKind value) {
          return JsonBundle.message("schema.add.mapping.kind.text", StringUtil.capitalizeWords(value.getDescription(), true));
        }

        @Override
        public Icon getIconFor(JsonMappingKind value) {
          return value.getIcon();
        }

        @Override
        public PopupStep<?> onChosen(JsonMappingKind selectedValue, boolean finalChoice) {
          if (finalChoice) {
            return doFinalStep(() -> doRun(selectedValue));
          }
          return FINAL_CHOICE;
        }
      }).show(point);
    }

    void doRun(JsonMappingKind mappingKind) {
      UserDefinedJsonSchemaConfiguration.Item currentItem = new UserDefinedJsonSchemaConfiguration.Item("", mappingKind);
      myTableView.getListTableModel().addRow(currentItem);
      myTableView.editCellAt(myTableView.getListTableModel().getRowCount() - 1, 0);

      myTreeUpdater.updateTree(false);
    }
  }

  private final class MyEditActionButtonRunnableImpl implements AnActionButtonRunnable {
    MyEditActionButtonRunnableImpl() {
      super();
    }

    @Override
    public void run(AnActionButton button) {
      execute();
    }

    public void execute() {
      int selectedRow = myTableView.getSelectedRow();
      if (selectedRow == -1) return;
      myTableView.editCellAt(selectedRow, 0);
    }
  }

  private final class MyRemoveActionButtonRunnable implements AnActionButtonRunnable {
    @Override
    public void run(AnActionButton button) {
      final int[] rows = myTableView.getSelectedRows();
      if (rows != null && rows.length > 0) {
        int cnt = 0;
        for (int row : rows) {
          myTableView.getListTableModel().removeRow(row - cnt);
          ++cnt;
        }
        myTableView.getListTableModel().fireTableDataChanged();
        myTreeUpdater.updateTree(true);
      }
    }
  }
}
