// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.ui;

import com.intellij.dvcs.DvcsRememberedInputs;
import com.intellij.dvcs.repo.ClonePathProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ComboBoxCompositeEditor;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.event.DocumentEvent;
import java.awt.Point;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @deprecated Migrate to {@link com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension}
 * or {@link com.intellij.openapi.vcs.ui.VcsCloneComponent}
 */
@Deprecated(forRemoval = true)
public abstract class CloneDvcsDialog extends DialogWrapper {

  protected final @NotNull Project myProject;
  protected final @NotNull String myVcsDirectoryName;

  private final @NotNull CloneDvcsDialogUi ui;

  private @Nullable ValidationInfo myCreateDirectoryValidationInfo;
  private @Nullable ValidationInfo myRepositoryTestValidationInfo;
  private @Nullable ProgressIndicator myRepositoryTestProgressIndicator;

  public CloneDvcsDialog(@NotNull Project project, @NotNull @Nls String displayName, @NotNull String vcsDirectoryName) {
    this(project, displayName, vcsDirectoryName, null);
  }

  public CloneDvcsDialog(@NotNull Project project,
                         @NotNull @Nls String displayName,
                         @NotNull String vcsDirectoryName,
                         @Nullable String defaultUrl) {
    super(project, true);
    myProject = project;
    myVcsDirectoryName = vcsDirectoryName;
    ui = new CloneDvcsDialogUi(project, getRememberedInputs());

    initComponents(defaultUrl);
    setTitle(DvcsBundle.message("clone.title"));
    setOKButtonText(DvcsBundle.message("clone.button"));
    init();
  }

  @Override
  protected void doOKAction() {
    String path = ui.directoryField.getText();
    new Task.Modal(myProject, DvcsBundle.message("progress.title.creating.destination.directory"), true) {
      private ValidationInfo error = null;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        error = CloneDvcsValidationUtils.createDestination(path);
      }

      @Override
      public void onSuccess() {
        if (error == null) {
          CloneDvcsDialog.super.doOKAction();
        }
        else {
          myCreateDirectoryValidationInfo = error;
          startTrackingValidation();
        }
      }
    }.queue();
  }

  public @NotNull String getSourceRepositoryURL() {
    return getCurrentUrlText();
  }

  public @NotNull String getParentDirectory() {
    Path parent = Paths.get(ui.directoryField.getText()).toAbsolutePath().getParent();
    return Objects.requireNonNull(parent).toAbsolutePath().toString();
  }

  public @NotNull String getDirectoryName() {
    return Paths.get(ui.directoryField.getText()).getFileName().toString();
  }

  private void initComponents(@Nullable String defaultUrl) {
    ui.repositoryUrlFieldSpinner.setVisible(false);

    Disposer.register(getDisposable(), ui.spinnerProgressManager);

    ui.repositoryUrlCombobox.setEditable(true);
    ui.repositoryUrlCombobox.setEditor(ComboBoxCompositeEditor.withComponents(ui.repositoryUrlField, ui.repositoryUrlFieldSpinner));
    ui.repositoryUrlCombobox.setModel(ui.repositoryUrlComboboxModel);

    ui.repositoryUrlField.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull com.intellij.openapi.editor.event.DocumentEvent event) {
        ui.directoryField.trySetChildPath(defaultDirectoryPath(ui.repositoryUrlField.getText().trim()));
      }
    });
    ui.repositoryUrlField.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull com.intellij.openapi.editor.event.DocumentEvent event) {
        myRepositoryTestValidationInfo = null;
      }
    });

    ui.testButton.addActionListener(e -> test());

    ui.directoryField.addBrowseFolderListener(myProject, FileChooserDescriptorFactory.createSingleFolderDescriptor()
      .withTitle(DvcsBundle.message("clone.destination.directory.browser.title"))
      .withDescription(DvcsBundle.message("clone.destination.directory.browser.description"))
      .withShowFileSystemRoots(true)
      .withHideIgnored(false));

    if (defaultUrl != null) {
      ui.repositoryUrlField.setText(defaultUrl);
      ui.repositoryUrlField.selectAll();
      ui.testButton.setEnabled(true);
    }
  }

  private void test() {
    String testUrl = getCurrentUrlText();
    if (myRepositoryTestProgressIndicator != null) {
      myRepositoryTestProgressIndicator.cancel();
      myRepositoryTestProgressIndicator = null;
    }
    myRepositoryTestProgressIndicator =
      ui.spinnerProgressManager
        .run(new Task.Backgroundable(myProject, DvcsBundle.message("clone.repository.url.test.title", testUrl), true) {
          private TestResult myTestResult;

          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            myTestResult = test(testUrl);
          }

          @Override
          public void onSuccess() {
            if (myTestResult.isSuccess()) {
              myRepositoryTestValidationInfo = null;
              Disposable dialogDisposable = getDisposable();
              if (Disposer.isDisposed(dialogDisposable)) return;
              JBPopupFactory.getInstance()
                .createBalloonBuilder(new JLabel(DvcsBundle.message("clone.repository.url.test.success.message")))
                .setDisposable(dialogDisposable)
                .createBalloon()
                .show(new RelativePoint(ui.testButton, new Point(ui.testButton.getWidth() / 2,
                                                                      ui.testButton.getHeight())),
                      Balloon.Position.below);
            }
            else {
              myRepositoryTestValidationInfo =
                new ValidationInfo(DvcsBundle.message("clone.repository.url.test.failed.message",
                                                      XmlStringUtil.escapeString(myTestResult.myErrorMessage)),
                                   ui.repositoryUrlCombobox);
              startTrackingValidation();
            }
            myRepositoryTestProgressIndicator = null;
          }
        });
  }

  protected abstract @NotNull TestResult test(@NotNull String url);

  protected abstract @NotNull DvcsRememberedInputs getRememberedInputs();

  @Override
  protected boolean continuousValidation() {
    // Force continuousValidation for custom validation in doValidateAll
    return true;
  }

  @Override
  protected @NotNull List<ValidationInfo> doValidateAll() {
    ValidationInfo urlValidation = CloneDvcsValidationUtils.checkRepositoryURL(ui.repositoryUrlCombobox, getCurrentUrlText());
    ValidationInfo directoryValidation = CloneDvcsValidationUtils.checkDirectory(ui.directoryField.getText(),
                                                                                 ui.directoryField.getTextField());

    ui.testButton.setEnabled(urlValidation == null);

    List<ValidationInfo> infoList = new ArrayList<>();
    ContainerUtil.addIfNotNull(infoList, myRepositoryTestValidationInfo);
    ContainerUtil.addIfNotNull(infoList, myCreateDirectoryValidationInfo);
    ContainerUtil.addIfNotNull(infoList, urlValidation);
    ContainerUtil.addIfNotNull(infoList, directoryValidation);
    return infoList;
  }

  private @NotNull String getCurrentUrlText() {
    return OSAgnosticPathUtil.expandUserHome(ui.repositoryUrlField.getText().trim());
  }

  /**
   * @deprecated use {@link #getRepositoryHostingServices()}
   */
  @Deprecated(forRemoval = true)
  public void prependToHistory(final @NotNull String item) {
    ui.repositoryUrlComboboxModel.add(item);
  }

  public void rememberSettings() {
    final DvcsRememberedInputs rememberedInputs = getRememberedInputs();
    rememberedInputs.addUrl(getSourceRepositoryURL());
    rememberedInputs.setCloneParentDir(getParentDirectory());
  }

  /**
   * Get default name for checked out directory
   *
   * @param url an URL to checkout
   * @return a default repository name
   */
  private @NotNull String defaultDirectoryPath(final @NotNull String url) {
    return StringUtil.trimEnd(ClonePathProvider.relativeDirectoryPathForVcsUrl(myProject, url), myVcsDirectoryName);
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return ui.repositoryUrlField;
  }

  @Override
  protected @NotNull JComponent createCenterPanel() {
    return ui.panel;
  }

  protected static class TestResult {
    public static final @NotNull TestResult SUCCESS = new TestResult(null);
    private final @Nullable String myErrorMessage;

    public TestResult(@Nullable String errorMessage) {
      myErrorMessage = errorMessage;
    }

    public boolean isSuccess() {
      return myErrorMessage == null;
    }

    public @Nullable String getError() {
      return myErrorMessage;
    }
  }

  static final class MyTextFieldWithBrowseButton extends TextFieldWithBrowseButton {
    private final @NotNull Path myDefaultParentPath;
    private boolean myModifiedByUser = false;

    MyTextFieldWithBrowseButton(@NotNull @NonNls String defaultParentPath) {
      myDefaultParentPath = Paths.get(defaultParentPath).toAbsolutePath();
      setText(myDefaultParentPath.toString());
      getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
          myModifiedByUser = true;
        }
      });
    }

    public void trySetChildPath(@NotNull String child) {
      if (!myModifiedByUser) {
        try {
          setText(myDefaultParentPath.resolve(child).toString());
        }
        catch (InvalidPathException ignored) {
        }
        finally {
          myModifiedByUser = false;
        }
      }
    }
  }
}
