// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ui;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.dvcs.DvcsRememberedInputs;
import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.hosting.RepositoryHostingService;
import com.intellij.dvcs.hosting.RepositoryListLoader;
import com.intellij.dvcs.hosting.RepositoryListLoadingException;
import com.intellij.dvcs.repo.ClonePathProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.progress.ComponentVisibilityProgressManager;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;

import static com.intellij.util.ui.UI.PanelFactory;

/**
 * @deprecated Migrate to {@link com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension}
 * or {@link com.intellij.openapi.vcs.ui.VcsCloneComponent}
 */
@Deprecated
public abstract class CloneDvcsDialog extends DialogWrapper {

  private ComboBox<String> myRepositoryUrlCombobox;
  private CollectionComboBoxModel<String> myRepositoryUrlComboboxModel;
  private TextFieldWithAutoCompletion<String> myRepositoryUrlField;
  private ComponentVisibilityProgressManager mySpinnerProgressManager;
  private JButton myTestButton; // test repository
  private MyTextFieldWithBrowseButton myDirectoryField;
  private LoginButtonComponent myLoginButtonComponent;

  @NotNull protected final Project myProject;
  @NotNull protected final String myVcsDirectoryName;

  @Nullable private ValidationInfo myCreateDirectoryValidationInfo;
  @Nullable private ValidationInfo myRepositoryTestValidationInfo;
  @Nullable private ProgressIndicator myRepositoryTestProgressIndicator;

  @NotNull private final List<String> myLoadedRepositoryHostingServicesNames;
  @Nullable private Alarm myRepositoryUrlAutoCompletionTooltipAlarm;
  @NotNull private final Set<String> myUniqueAvailableRepositories;
  @NotNull private final List<ValidationInfo> myRepositoryListLoadingErrors = new ArrayList<>();

  public CloneDvcsDialog(@NotNull Project project, @NotNull String displayName, @NotNull String vcsDirectoryName) {
    this(project, displayName, vcsDirectoryName, null);
  }

  public CloneDvcsDialog(@NotNull Project project,
                         @NotNull String displayName,
                         @NotNull String vcsDirectoryName,
                         @Nullable String defaultUrl) {
    super(project, true);
    myProject = project;
    myVcsDirectoryName = vcsDirectoryName;
    myLoadedRepositoryHostingServicesNames = new ArrayList<>();
    myUniqueAvailableRepositories = new HashSet<>();

    initComponents(defaultUrl);
    Map<String, RepositoryListLoader> loadersToSchedule = initUrlAutocomplete();
    setTitle(DvcsBundle.getString("clone.title"));
    setOKButtonText(DvcsBundle.getString("clone.button"));
    init();
    scheduleLater(loadersToSchedule);
  }

  @Override
  protected void doOKAction() {
    String path = myDirectoryField.getText();
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

  @NotNull
  public String getSourceRepositoryURL() {
    return getCurrentUrlText();
  }

  @NotNull
  public String getParentDirectory() {
    Path parent = Paths.get(myDirectoryField.getText()).toAbsolutePath().getParent();
    return Objects.requireNonNull(parent).toAbsolutePath().toString();
  }

  @NotNull
  public String getDirectoryName() {
    return Paths.get(myDirectoryField.getText()).getFileName().toString();
  }

  private void initComponents(@Nullable String defaultUrl) {
    myRepositoryUrlComboboxModel = new CollectionComboBoxModel<>();
    myRepositoryUrlField = TextFieldWithAutoCompletion.create(myProject,
                                                              myRepositoryUrlComboboxModel.getItems(),
                                                              false,
                                                              "");

    JLabel repositoryUrlFieldSpinner = new JLabel(new AnimatedIcon.Default());
    repositoryUrlFieldSpinner.setVisible(false);

    mySpinnerProgressManager = new ComponentVisibilityProgressManager(repositoryUrlFieldSpinner);
    Disposer.register(getDisposable(), mySpinnerProgressManager);

    myRepositoryUrlCombobox = new ComboBox<>();
    myRepositoryUrlCombobox.setEditable(true);
    myRepositoryUrlCombobox.setEditor(ComboBoxCompositeEditor.withComponents(myRepositoryUrlField,
                                                                             repositoryUrlFieldSpinner));
    myRepositoryUrlCombobox.setModel(myRepositoryUrlComboboxModel);

    myRepositoryUrlField.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull com.intellij.openapi.editor.event.DocumentEvent event) {
        myDirectoryField.trySetChildPath(defaultDirectoryPath(myRepositoryUrlField.getText().trim()));
      }
    });
    myRepositoryUrlField.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull com.intellij.openapi.editor.event.DocumentEvent event) {
        myRepositoryTestValidationInfo = null;
      }
    });

    myTestButton = new JButton(DvcsBundle.getString("clone.repository.url.test.label"));
    myTestButton.addActionListener(e -> test());

    FileChooserDescriptor fcd = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    fcd.setShowFileSystemRoots(true);
    fcd.setHideIgnored(false);
    myDirectoryField = new MyTextFieldWithBrowseButton(ClonePathProvider.defaultParentDirectoryPath(myProject, getRememberedInputs()));
    myDirectoryField.addBrowseFolderListener(DvcsBundle.getString("clone.destination.directory.browser.title"),
                                             DvcsBundle.getString("clone.destination.directory.browser.description"),
                                             myProject,
                                             fcd);

    if (defaultUrl != null) {
      myRepositoryUrlField.setText(defaultUrl);
      myRepositoryUrlField.selectAll();
      myTestButton.setEnabled(true);
    }
  }

  /**
   * Initializes component structure for repository list loading
   *
   * @return already enabled loaders for pre-scheduling
   */
  private Map<String, RepositoryListLoader> initUrlAutocomplete() {
    Collection<RepositoryHostingService> repositoryHostingServices = getRepositoryHostingServices();
    if (repositoryHostingServices.size() > 1) {
      myRepositoryUrlAutoCompletionTooltipAlarm = new Alarm(getDisposable());
      myRepositoryUrlAutoCompletionTooltipAlarm.setActivationComponent(myRepositoryUrlCombobox);
    }

    List<Action> loginActions = new ArrayList<>();
    Map<String, RepositoryListLoader> enabledLoaders = new HashMap<>();
    for (RepositoryHostingService service : repositoryHostingServices) {
      String serviceDisplayName = service.getServiceDisplayName();
      RepositoryListLoader loader = service.getRepositoryListLoader(myProject);
      if (loader == null) continue;
      if (loader.isEnabled()) {
        enabledLoaders.put(serviceDisplayName, loader);
      }
      else {
        loginActions.add(new AbstractAction(DvcsBundle.message("clone.repository.url.autocomplete.login.text", serviceDisplayName)) {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (loader.enable(myLoginButtonComponent.getPanel())) {
              myLoginButtonComponent.removeAction(this);
              schedule(serviceDisplayName, loader);
            }
          }
        });
      }
    }

    myRepositoryUrlField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        showRepositoryUrlAutoCompletionTooltip();
      }
    });

    myLoginButtonComponent = new LoginButtonComponent(loginActions);
    return enabledLoaders;
  }

  @NotNull
  protected Collection<RepositoryHostingService> getRepositoryHostingServices() {
    return Collections.emptyList();
  }

  private void scheduleLater(@NotNull Map<String, RepositoryListLoader> loaders) {
    ApplicationManager.getApplication().invokeLater(() -> loaders.forEach(this::schedule), ModalityState.stateForComponent(getRootPane()));
  }

  private void schedule(@NotNull String serviceDisplayName, @NotNull RepositoryListLoader loader) {
    mySpinnerProgressManager.run(new Task.Backgroundable(myProject, DvcsBundle.message("progress.title.visible")) {
      private final List<String> myNewRepositories = new ArrayList<>();
      private final List<RepositoryListLoadingException> myErrors = new ArrayList<>();

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        RepositoryListLoader.Result loadingResult =
          loader.getAvailableRepositoriesFromMultipleSources(indicator);
        for (String repository : loadingResult.getUrls()) {
          if (myUniqueAvailableRepositories.add(repository)) {
            myNewRepositories.add(repository);
          }
        }
        myErrors.addAll(loadingResult.getErrors());
      }

      @Override
      public void onSuccess() {
        if (mySpinnerProgressManager.getDisposed()) return;
        if (!myNewRepositories.isEmpty()) {
          // otherwise editor content will be reset
          myRepositoryUrlCombobox.setSelectedItem(myRepositoryUrlField.getText());
          myRepositoryUrlComboboxModel.addAll(myRepositoryUrlComboboxModel.getSize(), myNewRepositories);
          myRepositoryUrlField.setVariants(myRepositoryUrlComboboxModel.getItems());
        }
        myLoadedRepositoryHostingServicesNames.add(serviceDisplayName);
        showRepositoryUrlAutoCompletionTooltip();
        if (!myErrors.isEmpty()) {
          for (RepositoryListLoadingException error : myErrors) {
            StringBuilder errorMessageBuilder = new StringBuilder();
            errorMessageBuilder.append(error.getMessage());
            Throwable cause = error.getCause();
            if (cause != null) errorMessageBuilder.append(": ").append(cause.getMessage());
            myRepositoryListLoadingErrors.add(new ValidationInfo(errorMessageBuilder.toString()).asWarning().withOKEnabled());
          }
          startTrackingValidation();
        }
      }
    });
  }

  private void showRepositoryUrlAutoCompletionTooltip() {
    if (myRepositoryUrlAutoCompletionTooltipAlarm == null) {
      showRepositoryUrlAutoCompletionTooltipNow();
    }
    else {
      myRepositoryUrlAutoCompletionTooltipAlarm.cancelAllRequests();
      myRepositoryUrlAutoCompletionTooltipAlarm.addComponentRequest(this::showRepositoryUrlAutoCompletionTooltipNow, 1);
    }
  }

  private void showRepositoryUrlAutoCompletionTooltipNow() {
    if (!hasErrors(myRepositoryUrlCombobox) && !myLoadedRepositoryHostingServicesNames.isEmpty()) {
      Editor editor = myRepositoryUrlField.getEditor();
      if (editor == null) return;
      String completionShortcutText =
        KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION));
      HintManager.getInstance().showInformationHint(editor,
                                                    DvcsBundle.message("clone.repository.url.autocomplete.hint",
                                                                       DvcsUtil.joinWithAnd(myLoadedRepositoryHostingServicesNames, 0),
                                                                       completionShortcutText));
    }
  }

  private void test() {
    String testUrl = getCurrentUrlText();
    if (myRepositoryTestProgressIndicator != null) {
      myRepositoryTestProgressIndicator.cancel();
      myRepositoryTestProgressIndicator = null;
    }
    myRepositoryTestProgressIndicator =
      mySpinnerProgressManager
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
                .createBalloonBuilder(new JLabel(DvcsBundle.getString("clone.repository.url.test.success.message")))
                .setDisposable(dialogDisposable)
                .createBalloon()
                .show(new RelativePoint(myTestButton, new Point(myTestButton.getWidth() / 2,
                                                                myTestButton.getHeight())),
                      Balloon.Position.below);
            }
            else {
              myRepositoryTestValidationInfo =
                new ValidationInfo(DvcsBundle.message("clone.repository.url.test.failed.message", myTestResult.myErrorMessage),
                                   myRepositoryUrlCombobox);
              startTrackingValidation();
            }
            myRepositoryTestProgressIndicator = null;
          }
        });
  }

  @NotNull
  protected abstract TestResult test(@NotNull String url);

  @NotNull
  protected abstract DvcsRememberedInputs getRememberedInputs();

  @NotNull
  @Override
  protected List<ValidationInfo> doValidateAll() {
    ValidationInfo urlValidation = CloneDvcsValidationUtils.checkRepositoryURL(myRepositoryUrlCombobox, getCurrentUrlText());
    ValidationInfo directoryValidation = CloneDvcsValidationUtils.checkDirectory(myDirectoryField.getText(),
                                                                                 myDirectoryField.getTextField());

    myTestButton.setEnabled(urlValidation == null);

    List<ValidationInfo> infoList = new ArrayList<>();
    ContainerUtil.addIfNotNull(infoList, myRepositoryTestValidationInfo);
    ContainerUtil.addIfNotNull(infoList, myCreateDirectoryValidationInfo);
    ContainerUtil.addIfNotNull(infoList, urlValidation);
    ContainerUtil.addIfNotNull(infoList, directoryValidation);
    infoList.addAll(myRepositoryListLoadingErrors);
    return infoList;
  }

  @NotNull
  private String getCurrentUrlText() {
    return FileUtil.expandUserHome(myRepositoryUrlField.getText().trim());
  }

  /**
   * @deprecated use {@link #getRepositoryHostingServices()}
   */
  @Deprecated
  public void prependToHistory(@NotNull final String item) {
    myRepositoryUrlComboboxModel.add(item);
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
  @NotNull
  private String defaultDirectoryPath(@NotNull final String url) {
    return StringUtil.trimEnd(ClonePathProvider.relativeDirectoryPathForVcsUrl(myProject, url), myVcsDirectoryName);
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myRepositoryUrlField;
  }

  @NotNull
  @Override
  protected JPanel createSouthAdditionalPanel() {
    return myLoginButtonComponent.getPanel();
  }

  @Override
  @NotNull
  protected JComponent createCenterPanel() {
    JPanel panel = PanelFactory.grid()
      .add(PanelFactory.panel(JBUI.Panels.simplePanel(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP)
                                .addToCenter(myRepositoryUrlCombobox)
                                .addToRight(myTestButton))
             .withLabel(DvcsBundle.getString("clone.repository.url.label")))
      .add(PanelFactory.panel(myDirectoryField)
             .withLabel(DvcsBundle.getString("clone.destination.directory.label")))
      .createPanel();
    panel.setPreferredSize(new JBDimension(500, 50, true));
    return panel;
  }

  protected static class TestResult {
    @NotNull public static final TestResult SUCCESS = new TestResult(null);
    @Nullable private final String myErrorMessage;

    public TestResult(@Nullable String errorMessage) {
      myErrorMessage = errorMessage;
    }

    public boolean isSuccess() {
      return myErrorMessage == null;
    }

    @Nullable
    public String getError() {
      return myErrorMessage;
    }
  }

  private static class MyTextFieldWithBrowseButton extends TextFieldWithBrowseButton {
    @NotNull private final Path myDefaultParentPath;
    private boolean myModifiedByUser = false;

    private MyTextFieldWithBrowseButton(@NotNull String defaultParentPath) {
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

  private static class LoginButtonComponent {
    @NotNull private final JBOptionButton myButton;
    @NotNull private final JPanel myPanel;
    @NotNull private final List<Action> myActions;

    LoginButtonComponent(@NotNull List<Action> actions) {
      myButton = new JBOptionButton(ContainerUtil.getFirstItem(actions), getActionsAfterFirst(actions));
      myPanel = PanelFactory.panel(myButton)
        .withTooltip(DvcsBundle.getString("clone.repository.url.autocomplete.login.tooltip"))
        .createPanel();
      myPanel.setVisible(!actions.isEmpty());
      myPanel.setBorder(JBUI.Borders.emptyRight(16));
      myActions = new ArrayList<>(actions);
    }

    void removeAction(@NotNull Action action) {
      if (myActions.remove(action)) {
        if (!myActions.isEmpty()) {
          myButton.setAction(ContainerUtil.getFirstItem(myActions));
          myButton.setOptions(getActionsAfterFirst(myActions));
        }
        else {
          myButton.setAction(null);
          myButton.setOptions((Action[])null);
          myPanel.setVisible(false);
        }
      }
    }

    private static Action @NotNull [] getActionsAfterFirst(@NotNull List<Action> actions) {
      if (actions.size() <= 1) {
        return new Action[0];
      }
      else {
        return actions.subList(1, actions.size()).toArray(new Action[actions.size() - 1]);
      }
    }

    @NotNull
    public JPanel getPanel() {
      return myPanel;
    }
  }
}