package com.intellij.database.view.ui;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.DataGridNotifications;
import com.intellij.database.datagrid.HelpID;
import com.intellij.database.extractors.DataExtractorFactory;
import com.intellij.database.run.actions.DumpSource;
import com.intellij.database.settings.CsvSettings;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.function.Supplier;

/**
 * @author Liudmila Kornilova
 **/
public abstract class DumpDataDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(DumpDataDialog.class);
  protected final DumpDataForm myForm;
  protected final Project myProject;
  protected final DumpSource<?> mySource;

  private CopyToClipboardAction myCopyToClipboardAction;
  private ComponentValidator myDirectoryPathValidator;

  public DumpDataDialog(@NotNull Project project,
                        @NotNull DumpSource<?> source,
                        @Nullable Component parentComponent) {
    super(project, parentComponent, true, IdeModalityType.IDE);
    myProject = project;
    mySource = source;
    myForm = createForm(source, myProject);
    setTitle(DataGridBundle.message("settings.database.DumpDialog.title"));
    setOKButtonText(DataGridBundle.message("settings.database.DumpDialog.DumpToFile", DumpSource.getSize(mySource)));
    myForm.getExtractorComboBox().addItemListener(e -> updateActions());
    init();
    updateCopyToClipboardButton();
    invokeLaterAfterDialogShown(() -> myForm.updatePreview());

    installValidators();
  }

  protected @NotNull DumpDataForm createForm(@NotNull DumpSource<?> source, @NotNull Project project) {
    return new DumpDataForm(project, source, () -> getWindow(), CsvSettings.getSettings(), getDisposable(), false);
  }

  private void installValidators() {
    final JTextField outputDirectoryField = myForm.getOutputFileOrDirectoryField();
    Supplier<ValidationInfo> validator = () -> {
      String path = getDirPath();
      if (StringUtil.isEmptyOrSpaces(path)) {
        return new ValidationInfo(DataGridBundle.message("settings.database.DumpDialog.Errors.DirPathEmpty"), outputDirectoryField);
      }
      String message = myForm.getOutputPathManager().validatePath(path);
      return message == null ? null : new ValidationInfo(message, outputDirectoryField);
    };
    myDirectoryPathValidator = new ComponentValidator(getDisposable())
      .withValidator(validator)
      .installOn(outputDirectoryField);
    outputDirectoryField.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) { }

      @Override
      public void focusLost(FocusEvent e) {
        ComponentValidator.getInstance(outputDirectoryField).ifPresent(ComponentValidator::revalidate);
        updateActions();
      }
    });
    addDocumentListener(outputDirectoryField);
  }

  private String getDirPath() {
    return FileUtil.expandUserHome(myForm.getOutputFileOrDirectoryField().getText().trim());
  }

  private void addDocumentListener(JTextComponent component) {
    component.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        ComponentValidator.getInstance(component).ifPresent(ComponentValidator::revalidate);
        updateActions();
      }
    });
  }

  @Override
  protected @Nullable ValidationInfo doValidate() {
    myDirectoryPathValidator.revalidate();
    return myDirectoryPathValidator.getValidationInfo();
  }

  private void invokeLaterAfterDialogShown(@NotNull Runnable action) {
    Application application = ApplicationManager.getApplication();
    this.getWindow().addWindowListener(new WindowAdapter() {
      @Override
      public void windowOpened(WindowEvent e) {
        Window window = e.getWindow();
        application.invokeLater(action, ModalityState.stateForComponent(window));
        window.removeWindowListener(this);
      }
    });
  }

  @Override
  protected Action @NotNull [] createActions() {
    return DumpSource.getSize(mySource) > 1 ?
           new Action[]{getOKAction(), getCancelAction(), getHelpAction()} :
           new Action[]{getOKAction(), getCancelAction(), myCopyToClipboardAction, getHelpAction()};
  }

  @Override
  protected void createDefaultActions() {
    super.createDefaultActions();
    myCopyToClipboardAction = new CopyToClipboardAction();
  }

  @Override
  protected @NotNull String getHelpId() {
    return HelpID.DUMP_DATA_DIALOG;
  }

  @Override
  protected void doOKAction() {
    updateActions();
    if (!isOKActionEnabled()) return;
    if (!checkOverrideFile()) return;
    DataExtractorFactory factory = myForm.getFactory();
    if (factory == null) return;
    File file = getOutputFileOrDir();
    if (file.exists() && !checkFileWritable(file)) {
      super.doOKAction();
      return;
    }
    myForm.saveState();
    boolean exists = file.exists();
    if (DumpSource.getSize(mySource) == 1 && !file.exists()) {
      try {
        exists = file.createNewFile();
      }
      catch (IOException e) {
        LOG.warn(e);
        showError(file);
      }
    }
    if (exists) {
      ApplicationManager.getApplication().invokeLater(() -> {
        exportToFile(factory, file);
      });
    }
    super.doOKAction();
  }

  protected abstract void exportToFile(@NotNull DataExtractorFactory factory, @NotNull File file);

  private boolean checkFileWritable(@NotNull File file) {
    if (file.isDirectory()) return true;
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    if (virtualFile == null || !ReadonlyStatusHandler.ensureFilesWritable(myProject, virtualFile)) {
      showError(file);
      return false;
    }
    return true;
  }

  private void showError(@NotNull File file) {
    String message = DataGridBundle.message("settings.database.DumpDialog.CannotWriteFile", file.getPath());
    DataGridNotifications.EXTRACTORS_GROUP.createNotification(message, MessageType.WARNING).setDisplayId("DumpDataDialog.write.failed")
      .notify(myProject);
  }

  private @NotNull File getOutputFileOrDir() {
    return Paths.get(getDirPath()).toFile();
  }

  private boolean checkOverrideFile() {
    if (DumpSource.getSize(mySource) > 1) return true; // files will not be overwritten. New files names will be generated instead
    File file = getOutputFileOrDir();
    return !file.exists() || askToOverrideFiles(file.getName());
  }

  private boolean askToOverrideFiles(@NotNull String fileName) {
    int res = Messages.showYesNoDialog(getRootPane(),
                                       DataGridBundle.message("settings.database.DumpDialog.ConfirmReplace.message", fileName),
                                       DataGridBundle.message("settings.database.DumpDialog.ConfirmReplace.title"),
                                       Messages.getWarningIcon());
    return res == Messages.YES;
  }

  @Override
  public @NotNull JComponent getPreferredFocusedComponent() {
    return myForm.getExtractorComboBox();
  }

  @Override
  public void doCancelAction() {
    myForm.saveState();
    super.doCancelAction();
  }

  private void updateActions() {
    updateCopyToClipboardButton();

    getOKAction().setEnabled(myDirectoryPathValidator.getValidationInfo() == null);
  }

  private void updateCopyToClipboardButton() {
    DataExtractorFactory extractorFactory =
      ObjectUtils.tryCast(myForm.getExtractorComboBox().getSelectedItem(), DataExtractorFactory.class);
    myCopyToClipboardAction.setEnabled(extractorFactory != null && extractorFactory.supportsText());
  }

  @Override
  protected @NotNull JComponent createCenterPanel() {
    return myForm.myPanel;
  }

  private class CopyToClipboardAction extends DialogWrapperAction {
    CopyToClipboardAction() {
      super(DataGridBundle.message("settings.database.DumpDialog.CopyToClipboard"));
    }

    @Override
    protected void doAction(ActionEvent e) {
      myForm.saveState();
      close(OK_EXIT_CODE);
      DataExtractorFactory factory = myForm.getFactory();
      if (factory != null) {
        ApplicationManager.getApplication().invokeLater(() -> {
          exportToClipboard(factory);
        });
      }
    }
  }

  protected abstract void exportToClipboard(@NotNull DataExtractorFactory factory);
}
