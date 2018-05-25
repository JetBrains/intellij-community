// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.icons.AllIcons;
import com.intellij.json.JsonBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.TableView;
import com.intellij.util.Alarm;
import com.intellij.util.ui.*;
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.jetbrains.jsonSchema.JsonSchemaConfigurable.isHttpPath;
import static com.jetbrains.jsonSchema.JsonSchemaConfigurable.isValidURL;

/**
 * @author Irina.Chernushina on 2/2/2016.
 */
public class JsonSchemaMappingsView implements Disposable {
  private static final String ADD_SCHEMA_MAPPING = "settings.json.schema.add.mapping";
  private static final String EDIT_SCHEMA_MAPPING = "settings.json.schema.edit.mapping";
  private final Runnable myTreeUpdater;
  private final Consumer<String> mySchemaPathChangedCallback;
  private TableView<UserDefinedJsonSchemaConfiguration.Item> myTableView;
  private JComponent myComponent;
  private Project myProject;
  private TextFieldWithBrowseButton mySchemaField;
  private ComboBox<JsonSchemaVersion> mySchemaVersionComboBox;
  private JEditorPane myError;
  private String myErrorText;
  private JBLabel myErrorIcon;
  private boolean myInitialized;

  public JsonSchemaMappingsView(Project project,
                                Runnable treeUpdater,
                                Consumer<String> schemaPathChangedCallback) {
    myTreeUpdater = treeUpdater;
    mySchemaPathChangedCallback = schemaPathChangedCallback;
    createUI(project);
  }

  private void createUI(final Project project) {
    myProject = project;
    myTableView = new JsonMappingsTableView();
    myTableView.getTableHeader().setVisible(false);
    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myTableView);
    final MyEditActionButtonRunnableImpl editAction = new MyEditActionButtonRunnableImpl(project);
    decorator.setRemoveAction(new MyRemoveActionButtonRunnable())
             .setAddAction(new MyAddActionButtonRunnable(project))
             .setEditAction(editAction)
             .disableUpDownActions();

    myTableView.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          editAction.execute();
        }
      }
    });

    JBTextField schemaFieldBacking = new JBTextField();
    mySchemaField = new TextFieldWithBrowseButton(schemaFieldBacking);
    SwingHelper.installFileCompletionAndBrowseDialog(myProject, mySchemaField, JsonBundle.message("json.schema.add.schema.chooser.title"),
                                                     FileChooserDescriptorFactory.createSingleFileDescriptor());
    mySchemaField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        mySchemaPathChangedCallback.accept(mySchemaField.getText());
      }
    });
    attachNavigateToSchema();
    myError = SwingHelper.createHtmlLabel(JsonBundle.message("json.schema.conflicting.mappings"), null, s -> {
      final BalloonBuilder builder = JBPopupFactory.getInstance().
        createHtmlTextBalloonBuilder(myErrorText, UIUtil.getBalloonWarningIcon(), MessageType.WARNING.getPopupBackground(), null);
      builder.setDisposable(this);
      builder.setHideOnClickOutside(true);
      builder.setCloseButtonEnabled(true);
      builder.createBalloon().showInCenterOf(myError);
    });

    final FormBuilder builder = FormBuilder.createFormBuilder();
    final ErrorLabel label = new ErrorLabel(JsonBundle.message("json.schema.file.selector.title"));
    schemaFieldBacking.getDocument().addDocumentListener(new SchemaFieldErrorMessageProvider(schemaFieldBacking, label));
    if (schemaFieldBacking.getText().isEmpty()) {
      label.setErrorText("Schema path cannot be empty", JBColor.RED);
    }
    builder.addLabeledComponent(label, mySchemaField);
    label.setLabelFor(mySchemaField);
    label.setBorder(JBUI.Borders.empty(0, 10));
    mySchemaField.setBorder(JBUI.Borders.emptyRight(10));
    JBLabel versionLabel = new JBLabel("Schema version:");
    mySchemaVersionComboBox = new ComboBox<>(new DefaultComboBoxModel<>(JsonSchemaVersion.values()));
    versionLabel.setLabelFor(mySchemaVersionComboBox);
    versionLabel.setBorder(JBUI.Borders.empty(0, 10));
    builder.addLabeledComponent(versionLabel, mySchemaVersionComboBox);
    final JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.setBorder(JBUI.Borders.empty(0, 10));
    myErrorIcon = new JBLabel(UIUtil.getBalloonWarningIcon());
    wrapper.add(myErrorIcon, BorderLayout.WEST);
    wrapper.add(myError, BorderLayout.CENTER);
    builder.addComponent(wrapper);
    builder.addComponentFillVertically(decorator.createPanel(), 5);

    myComponent = builder.getPanel();
  }

  @Override
  public void dispose() {
  }

  public void setError(final String text) {
    myErrorText = text;
    myError.setVisible(text != null);
    myErrorIcon.setVisible(text != null);
  }

  private void attachNavigateToSchema() {
    DumbAwareAction.create(e -> {
      String pathToSchema = mySchemaField.getText();
      if (StringUtil.isEmptyOrSpaces(pathToSchema) || isHttpPath(pathToSchema)) return;
      VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(pathToSchema));
      if (virtualFile == null) {
        BalloonBuilder balloonBuilder = JBPopupFactory.getInstance()
          .createHtmlTextBalloonBuilder(JsonBundle.message("json.schema.file.not.found"), UIUtil.getBalloonErrorIcon(), MessageType.ERROR.getPopupBackground(), null);
        Balloon balloon = balloonBuilder.setFadeoutTime(TimeUnit.SECONDS.toMillis(3)).createBalloon();
        balloon.showInCenterOf(mySchemaField);
        return;
      }
      new OpenFileDescriptor(myProject, virtualFile).navigate(true);
    }).registerCustomShortcutSet(CommonShortcuts.getEditSource(), mySchemaField);
  }

  public List<UserDefinedJsonSchemaConfiguration.Item> getData() {
    return myTableView.getListTableModel().getItems();
  }

  public void setItems(String schemaFilePath,
                       JsonSchemaVersion version,
                       final List<UserDefinedJsonSchemaConfiguration.Item> data) {
    myInitialized = true;
    mySchemaField.setText(schemaFilePath);
    mySchemaVersionComboBox.setSelectedItem(version);
    myTableView.setModelAndUpdateColumns(
      new ListTableModel<>(createColumns(), new ArrayList<>(data)));
  }

  public boolean isInitialized() {
    return myInitialized;
  }

  public JsonSchemaVersion getSchemaVersion() {
    return (JsonSchemaVersion)mySchemaVersionComboBox.getSelectedItem();
  }

  public String getSchemaSubPath() {
    String schemaFieldText = mySchemaField.getText();
    if (isHttpPath(schemaFieldText)) return schemaFieldText;
    return FileUtil.toSystemDependentName(JsonSchemaInfo.getRelativePath(myProject, schemaFieldText));
  }

  private static ColumnInfo[] createColumns() {
    return new ColumnInfo[] { new MappingItemColumnInfo() };
  }

  public JComponent getComponent() {
    return myComponent;
  }

  private static class MappingItemColumnInfo extends ColumnInfo<UserDefinedJsonSchemaConfiguration.Item, String> {
    public MappingItemColumnInfo() {super("");}

    @Nullable
    @Override
    public String valueOf(UserDefinedJsonSchemaConfiguration.Item item) {
      return item.getPresentation();
    }

    @NotNull
    @Override
    public TableCellRenderer getRenderer(UserDefinedJsonSchemaConfiguration.Item item) {
      return new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
          JLabel label = (JLabel)super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          label.setIcon(item.directory ? AllIcons.Nodes.Folder : item.pattern ? AllIcons.FileTypes.Unknown : AllIcons.FileTypes.Any_type);
          return label;
        }
      };
    }
  }

  private abstract class MyAddOrEditActionButtonRunnableBase implements AnActionButtonRunnable {
    private final Project myProject;

    public MyAddOrEditActionButtonRunnableBase(Project project) {
      myProject = project;
    }

    @Override
    public abstract void run(AnActionButton button);

    protected void doRun(@Nullable UserDefinedJsonSchemaConfiguration.Item currentItem, int selectedRow) {
      final JBPanel panel = new JBPanel(new GridBagLayout());
      final GridBag bag = new GridBag();

      final JBTextField patternField = new JBTextField();
      final TextFieldWithBrowseButton directoryField = new TextFieldWithBrowseButton();
      if (currentItem != null && currentItem.directory) {
        directoryField.setText(currentItem.path);
      }
      final TextFieldWithBrowseButton fileField = new TextFieldWithBrowseButton();
      if (currentItem != null && !currentItem.directory && !currentItem.pattern) {
        fileField.setText(currentItem.path);
      }

      bag.setDefaultAnchor(GridBagConstraints.NORTHWEST);
      final JBRadioButton radioPattern = new JBRadioButton("Filename pattern:");
      final JBRadioButton radioDirectory = new JBRadioButton("Files under:");
      final JBRadioButton radioFile = new JBRadioButton("File:");

      panel.add(radioDirectory, bag.nextLine().next().fillCellNone().weightx(0));
      panel.add(directoryField, bag.next().fillCellHorizontally().weightx(1));

      panel.add(radioPattern, bag.nextLine().next().fillCellNone().weightx(0));
      panel.add(patternField, bag.next().fillCellHorizontally().weightx(1));

      panel.add(radioFile, bag.nextLine().next().fillCellNone().weightx(0));
      panel.add(fileField, bag.next().fillCellHorizontally().weightx(1));

      SwingHelper.installFileCompletionAndBrowseDialog(myProject, directoryField, "Select Folder", FileChooserDescriptorFactory.createSingleFolderDescriptor());
      SwingHelper.installFileCompletionAndBrowseDialog(myProject, fileField, "Select File", FileChooserDescriptorFactory.createSingleFileDescriptor());

      final ButtonGroup group = new ButtonGroup();
      group.add(radioPattern);
      group.add(radioDirectory);
      group.add(radioFile);

      if (currentItem == null) {
        radioDirectory.setSelected(true);
      }
      else {
        if (currentItem.pattern) radioPattern.setSelected(true);
        else if (currentItem.directory) radioDirectory.setSelected(true);
        else radioFile.setSelected(true);
      }

      patternField.setMinimumSize(new Dimension(JBUI.scale(200), UIUtil.getInformationIcon().getIconHeight()));
      patternField.getEmptyText().setText("Example: *.config.json");
      if (currentItem != null && currentItem.pattern) {
        patternField.setText(currentItem.path);
      }

      final DialogBuilder builder = new DialogBuilder();
      String addOrEdit = currentItem == null ? "Add" : "Edit";
      builder.setTitle(addOrEdit + " JSON Schema Mapping");
      builder.setNorthPanel(panel);
      builder.setPreferredFocusComponent(directoryField);
      builder.setDimensionServiceKey("com.jetbrains.jsonSchema.JsonSchemaMappingsView#add");
      builder.setHelpId(currentItem == null ? ADD_SCHEMA_MAPPING : EDIT_SCHEMA_MAPPING);

      final Getter<String> textGetter = () -> {
        if (radioPattern.isSelected()) {
          return patternField.getText();
        }

        final String text;
        if (radioDirectory.isSelected()) {
          text = directoryField.getText();
        } else {
          text = fileField.getText();
        }
        return JsonSchemaInfo.getRelativePath(myProject, text);
      };
      final Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
      final Runnable updaterValidator = new Runnable() {
        @Override
        public void run() {
          if (!patternField.isVisible()) return;
          patternField.setEnabled(radioPattern.isSelected());
          directoryField.setEnabled(radioDirectory.isSelected());
          fileField.setEnabled(radioFile.isSelected());
          builder.setOkActionEnabled(!StringUtil.isEmptyOrSpaces(textGetter.get()));

          alarm.addRequest(this, 300, ModalityState.any());
        }
      };
      alarm.addRequest(updaterValidator, 300, ModalityState.any());
      updaterValidator.run();
      final ActionListener listener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          updaterValidator.run();
        }
      };
      radioPattern.addActionListener(listener);
      radioDirectory.addActionListener(listener);
      radioFile.addActionListener(listener);

      if (myProject == null || myProject.getBasePath() == null) {
        radioDirectory.setEnabled(false);
        radioFile.setEnabled(false);
        directoryField.setEnabled(false);
        fileField.setEnabled(false);
      }

      if (builder.showAndGet()) {
        final String pattern = textGetter.get();
        final UserDefinedJsonSchemaConfiguration.Item item =
          new UserDefinedJsonSchemaConfiguration.Item(pattern, radioPattern.isSelected(), radioDirectory.isSelected());
        if (currentItem != null) {
          myTableView.getListTableModel().removeRow(selectedRow);
          myTableView.getListTableModel().insertRow(selectedRow, item);
          myTableView.setSelection(Collections.singleton(item));
        }
        else {
          myTableView.getListTableModel().addRow(item);
        }
        myTreeUpdater.run();
      }
      Disposer.dispose(alarm);
    }
  }

  private class MyAddActionButtonRunnable extends MyAddOrEditActionButtonRunnableBase {
    public MyAddActionButtonRunnable(Project project) {
      super(project);
    }

    @Override
    public void run(AnActionButton button) {
      doRun(null, -1);
    }
  }

  private class MyEditActionButtonRunnableImpl extends MyAddOrEditActionButtonRunnableBase {
    public MyEditActionButtonRunnableImpl(Project project) {
      super(project);
    }

    @Override
    public void run(AnActionButton button) {
      execute();
    }

    public void execute() {
      int selectedRow = myTableView.getSelectedRow();
      if (selectedRow == -1) return;
      UserDefinedJsonSchemaConfiguration.Item item = myTableView.getListTableModel().getItem(selectedRow);
      if (item == null) return;
      doRun(item, selectedRow);
    }
  }

  private class MyRemoveActionButtonRunnable implements AnActionButtonRunnable {
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
        myTreeUpdater.run();
      }
    }
  }

  private static class SchemaFieldErrorMessageProvider extends DocumentAdapter {
    private final JBTextField mySchemaFieldBacking;
    private final ErrorLabel myLabel;

    public SchemaFieldErrorMessageProvider(JBTextField schemaFieldBacking, ErrorLabel label) {
      mySchemaFieldBacking = schemaFieldBacking;
      myLabel = label;
    }

    protected void textChanged(DocumentEvent e) {
      String text = mySchemaFieldBacking.getText().trim();
      if (text.isEmpty()) {
        myLabel.setErrorText("Schema path cannot be empty", JBColor.RED);
        return;
      }

      if (isHttpPath(text)) {
        if (!isValidURL(text)) {
          myLabel.setErrorText("Invalid schema URL", JBColor.RED);
          return;
        }
      }
      else if (!new File(text).exists()) {
        myLabel.setErrorText("Schema file doesn't exist", JBColor.RED);
        return;
      }
      myLabel.setErrorText(null, null);
    }
  }

  private class JsonMappingsTableView extends TableView<UserDefinedJsonSchemaConfiguration.Item> {
    private final StatusText myEmptyText;

    public JsonMappingsTableView() {
      myEmptyText = new StatusText() {
        @Override
        protected boolean isStatusVisible() {
          return isEmpty();
        }
      };
      myEmptyText.setText("No schema mappings defined");
      myEmptyText.appendSecondaryText("Add mapping for a file, folder or pattern", SimpleTextAttributes.LINK_ATTRIBUTES,
                                      e -> new MyAddActionButtonRunnable(myProject).doRun(null, -1));
    }

    @NotNull
    @Override
    public StatusText getEmptyText() {
      return myEmptyText;
    }
  }
}
