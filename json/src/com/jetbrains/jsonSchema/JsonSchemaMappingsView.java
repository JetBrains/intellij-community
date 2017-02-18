package com.jetbrains.jsonSchema;

import com.intellij.json.JsonBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
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
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.TableView;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Irina.Chernushina on 2/2/2016.
 */
public class JsonSchemaMappingsView implements Disposable {
  private static final String ADD_SCHEMA_MAPPING = "settings.json.schema.add.mapping";
  private final Runnable myTreeUpdater;
  private TableView<JsonSchemaMappingsConfigurationBase.Item> myTableView;
  private ToolbarDecorator myDecorator;
  private JComponent myComponent;
  private Project myProject;
  private TextFieldWithBrowseButton mySchemaField;
  private JEditorPane myError;
  private String myErrorText;
  private JBLabel myErrorIcon;
  private boolean myInitialized;

  public JsonSchemaMappingsView(Project project, Runnable treeUpdater) {
    myTreeUpdater = treeUpdater;
    createUI(project);
  }

  private void createUI(final Project project) {
    myProject = project;
    myTableView = new TableView<>();
    myTableView.getTableHeader().setVisible(false);
    myDecorator = ToolbarDecorator.createDecorator(myTableView);
    myDecorator
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          final int[] rows = myTableView.getSelectedRows();
          if (rows != null && rows.length > 0) {
            int cnt = 0;
            for (int row : rows) {
              myTableView.getListTableModel().removeRow(row - cnt);
              ++ cnt;
            }
            myTableView.getListTableModel().fireTableDataChanged();
            myTreeUpdater.run();
          }
        }
      })
      .setAddAction(new MyAddActionButtonRunnable(project))
      .disableUpDownActions();

    mySchemaField = new TextFieldWithBrowseButton();
    SwingHelper.installFileCompletionAndBrowseDialog(myProject, mySchemaField, JsonBundle.message("json.schema.add.schema.chooser.title"),
                                                     FileChooserDescriptorFactory.createSingleFileDescriptor());
    attachNavigateToSchema();
    myError = SwingHelper.createHtmlLabel("Warning: conflicting mappings. <a href=\"#\">Show details</a>", null, s -> {
      final BalloonBuilder builder = JBPopupFactory.getInstance().
        createHtmlTextBalloonBuilder(myErrorText, UIUtil.getBalloonWarningIcon(), MessageType.WARNING.getPopupBackground(), null);
      builder.setDisposable(this);
      builder.setHideOnClickOutside(true);
      builder.setCloseButtonEnabled(true);
      builder.createBalloon().showInCenterOf(myError);
    });

    final FormBuilder builder = FormBuilder.createFormBuilder();
    final JBLabel label = new JBLabel("JSON schema file:");
    builder.addLabeledComponent(label, mySchemaField);
    label.setBorder(JBUI.Borders.empty(0,10,0,10));
    mySchemaField.setBorder(JBUI.Borders.empty(0, 0, 0, 10));
    final JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.setBorder(JBUI.Borders.empty(0, 10, 0, 10));
    myErrorIcon = new JBLabel(UIUtil.getBalloonWarningIcon());
    wrapper.add(myErrorIcon, BorderLayout.WEST);
    wrapper.add(myError, BorderLayout.CENTER);
    builder.addComponent(wrapper);
    builder.addComponentFillVertically(myDecorator.createPanel(), 5);

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
    new DumbAwareAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final String pathToSchema = mySchemaField.getText();
        if (StringUtil.isEmptyOrSpaces(pathToSchema)) return;
        final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(pathToSchema));
        if (virtualFile == null) {
          BalloonBuilder balloonBuilder = JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder("File not found", UIUtil.getBalloonErrorIcon(), MessageType.ERROR.getPopupBackground(), null);
          final Balloon balloon = balloonBuilder.setFadeoutTime(TimeUnit.SECONDS.toMillis(3)).createBalloon();
          balloon.showInCenterOf(mySchemaField);
          return;
        }
        new OpenFileDescriptor(myProject, virtualFile).navigate(true);
      }
    }.registerCustomShortcutSet(CommonShortcuts.getEditSource(), mySchemaField);
  }

  public List<JsonSchemaMappingsConfigurationBase.Item> getData() {
    return myTableView.getListTableModel().getItems();
  }

  public void setItems(String schemaFilePath, final List<JsonSchemaMappingsConfigurationBase.Item> data) {
    myInitialized = true;
    mySchemaField.setText(schemaFilePath);
    myTableView.setModelAndUpdateColumns(
      new ListTableModel<>(createColumns(), new ArrayList<>(data)));
  }

  public boolean isInitialized() {
    return myInitialized;
  }

  public String getSchemaSubPath() {
    return FileUtil.toSystemDependentName(getRelativePath(myProject, mySchemaField.getText()));
  }

  private static ColumnInfo[] createColumns() {
    return new ColumnInfo[] {
      new ColumnInfo<JsonSchemaMappingsConfigurationBase.Item, String>("") {
        @Nullable
        @Override
        public String valueOf(JsonSchemaMappingsConfigurationBase.Item item) {
          return item.getPresentation();
        }
      }
    };
  }

  public JComponent getComponent() {
    return myComponent;
  }

  private class MyAddActionButtonRunnable implements AnActionButtonRunnable {
    private final Project myProject;

    public MyAddActionButtonRunnable(Project project) {
      myProject = project;
    }

    @Override
    public void run(AnActionButton button) {
      final JBPanel panel = new JBPanel(new GridBagLayout());
      final GridBag bag = new GridBag();

      final JBTextField patternField = new JBTextField();
      final TextFieldWithBrowseButton directoryField = new TextFieldWithBrowseButton();
      final TextFieldWithBrowseButton fileField = new TextFieldWithBrowseButton();

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

      radioDirectory.setSelected(true);

      patternField.setMinimumSize(new Dimension(JBUI.scale(200), UIUtil.getInformationIcon().getIconHeight()));
      patternField.getEmptyText().setText("Example: *.config.json");

      final DialogBuilder builder = new DialogBuilder();
      builder.setTitle("Add JSON Schema Mapping");
      builder.setNorthPanel(panel);
      builder.setPreferredFocusComponent(directoryField);
      builder.setDimensionServiceKey("com.jetbrains.jsonSchema.JsonSchemaMappingsView#add");
      builder.setHelpId(ADD_SCHEMA_MAPPING);

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
        return getRelativePath(myProject, text);
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
        final JsonSchemaMappingsConfigurationBase.Item item =
          new JsonSchemaMappingsConfigurationBase.Item(pattern, radioPattern.isSelected(), radioDirectory.isSelected());
        myTableView.getListTableModel().addRow(item);
        myTreeUpdater.run();
      }
      Disposer.dispose(alarm);
    }
  }

  private static String getRelativePath(@NotNull Project project, @NotNull String text) {
    text = text.trim();
    if (project.isDefault()) return text;
    if (StringUtil.isEmptyOrSpaces(text)) return text;
    final File ioFile = new File(text);
    if (!ioFile.isAbsolute()) return text;
    final String relativePath = FileUtil.getRelativePath(new File(project.getBasePath()), ioFile);
    return relativePath == null ? text : relativePath;
  }
}
