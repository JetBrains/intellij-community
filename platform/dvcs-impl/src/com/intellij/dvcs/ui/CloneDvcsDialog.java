/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.dvcs.ui;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.dvcs.DvcsRememberedInputs;
import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.hosting.RepositoryHostingService;
import com.intellij.dvcs.hosting.RepositoryListLoader;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
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
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.progress.ComponentVisibilityProgressManager;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

import static com.intellij.util.ui.UI.PanelFactory;

public abstract class CloneDvcsDialog extends DialogWrapper {
  /**
   * The pattern for SSH URL-s in form [user@]host:path
   */
  private static final Pattern SSH_URL_PATTERN;

  static {
    // TODO make real URL pattern
    @NonNls final String ch = "[\\p{ASCII}&&[\\p{Graph}]&&[^@:/]]";
    @NonNls final String host = ch + "+(?:\\." + ch + "+)*";
    @NonNls final String path = "/?" + ch + "+(?:/" + ch + "+)*/?";
    @NonNls final String all = "(?:" + ch + "+@)?" + host + ":" + path;
    SSH_URL_PATTERN = Pattern.compile(all);
  }

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
    myCreateDirectoryValidationInfo = createDestination();
    super.doOKAction();
  }

  @Nullable
  private ValidationInfo createDestination() {
    Path directoryPath = Paths.get(myDirectoryField.getText());
    if (!Files.exists(directoryPath)) {
      try {
        Files.createDirectories(directoryPath);
      }
      catch (Exception e) {
        return new ValidationInfo(DvcsBundle.getString("clone.destination.directory.error.access"));
      }
    }
    else if (!Files.isDirectory(directoryPath) || !Files.isWritable(directoryPath)) {
      return new ValidationInfo(DvcsBundle.getString("clone.destination.directory.error.access"));
    }
    return null;
  }

  @NotNull
  public String getSourceRepositoryURL() {
    return getCurrentUrlText();
  }

  @NotNull
  public String getParentDirectory() {
    Path parent = Paths.get(myDirectoryField.getText()).toAbsolutePath().getParent();
    return ObjectUtils.assertNotNull(parent).toAbsolutePath().toString();
  }

  @NotNull
  public String getDirectoryName() {
    return Paths.get(myDirectoryField.getText()).getFileName().toString();
  }

  private void initComponents(@Nullable String defaultUrl) {
    String parentDirectory = getRememberedInputs().getCloneParentDir();

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
      public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent event) {
        startTrackingValidation();
      }
    });
    myRepositoryUrlField.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent event) {
        myDirectoryField.trySetChildPath(defaultDirectoryName(myRepositoryUrlField.getText().trim()));
      }
    });
    myRepositoryUrlField.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent event) {
        myRepositoryTestValidationInfo = null;
      }
    });

    myTestButton = new JButton(DvcsBundle.getString("clone.repository.url.test.label"));
    myTestButton.addActionListener(e -> test());

    FileChooserDescriptor fcd = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    fcd.setShowFileSystemRoots(true);
    fcd.setHideIgnored(false);
    myDirectoryField = new MyTextFieldWithBrowseButton(StringUtil.isEmptyOrSpaces(parentDirectory)
                                                       ? ProjectUtil.getBaseDir()
                                                       : parentDirectory);
    myDirectoryField.addBrowseFolderListener(DvcsBundle.getString("clone.destination.directory.browser.title"),
                                             DvcsBundle.getString("clone.destination.directory.browser.description"),
                                             myProject,
                                             fcd);
    myDirectoryField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        startTrackingValidation();
      }
    });

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
      if(loader == null) continue;
      if (loader.isEnabled()) {
        enabledLoaders.put(serviceDisplayName, loader);
      }
      else {
        loginActions.add(new AbstractAction(DvcsBundle.message("clone.repository.url.autocomplete.login.text", serviceDisplayName)) {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (loader.enable()) {
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
    mySpinnerProgressManager.run(new Task.Backgroundable(myProject, "Not Visible") {
      private final List<String> myNewRepositories = new ArrayList<>();

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (String repository : loader.getAvailableRepositories(indicator)) {
          if (myUniqueAvailableRepositories.add(repository)) {
            myNewRepositories.add(repository);
          }
        }
      }

      @Override
      public void onSuccess() {
        if (!myNewRepositories.isEmpty()) {
          // otherwise editor content will be reset
          myRepositoryUrlCombobox.setSelectedItem(myRepositoryUrlField.getText());
          myRepositoryUrlComboboxModel.addAll(myRepositoryUrlComboboxModel.getSize(), myNewRepositories);
          myRepositoryUrlField.setVariants(myRepositoryUrlComboboxModel.getItems());
        }
        myLoadedRepositoryHostingServicesNames.add(serviceDisplayName);
        showRepositoryUrlAutoCompletionTooltip();
      }

      @Override
      public void onThrowable(@NotNull Throwable error) {
        //TODO: show warning
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
      String completionShortcutText =
        KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION));
      HintManager.getInstance().showInformationHint(
        Objects.requireNonNull(myRepositoryUrlField.getEditor()),
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
              JBPopupFactory.getInstance()
                            .createBalloonBuilder(new JLabel(DvcsBundle.getString("clone.repository.url.test.success.message")))
                            .setDisposable(getDisposable())
                            .createBalloon()
                            .show(new RelativePoint(myTestButton, new Point(myTestButton.getWidth() / 2,
                                                                            myTestButton.getHeight())),
                                  Balloon.Position.below);
            }
            else {
              myRepositoryTestValidationInfo =
                new ValidationInfo(DvcsBundle.message("clone.repository.url.test.failed.message", myTestResult.myErrorMessage),
                                   myRepositoryUrlCombobox);
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
    ValidationInfo urlValidation = checkRepositoryURL();
    ValidationInfo directoryValidation = checkDirectory();

    myTestButton.setEnabled(urlValidation == null);

    List<ValidationInfo> infoList = new ArrayList<>();
    ContainerUtil.addIfNotNull(infoList, myRepositoryTestValidationInfo);
    ContainerUtil.addIfNotNull(infoList, myCreateDirectoryValidationInfo);
    ContainerUtil.addIfNotNull(infoList, urlValidation);
    ContainerUtil.addIfNotNull(infoList, directoryValidation);
    return infoList;
  }

  /**
   * Check repository URL and set appropriate error text if there are problems
   *
   * @return null if repository URL is OK.
   */
  @Nullable
  private ValidationInfo checkRepositoryURL() {
    String repository = getCurrentUrlText();
    if (repository.length() == 0) {
      return new ValidationInfo(DvcsBundle.getString("clone.repository.url.error.empty"), myRepositoryUrlCombobox);
    }

    // Is it a proper URL?
    try {
      if (new URI(repository).isAbsolute()) {
        return null;
      }
    }
    catch (URISyntaxException urlExp) {
      // do nothing
    }

    // Is it SSH URL?
    if (SSH_URL_PATTERN.matcher(repository).matches()) {
      return null;
    }

    // Is it FS URL?
    try {
      Path path = Paths.get(repository);

      if (Files.exists(path)) {
        if (!Files.isDirectory(path)) {
          return new ValidationInfo(DvcsBundle.getString("clone.repository.url.error.not.directory"), myRepositoryUrlCombobox);
        }
        return null;
      }
    }
    catch (Exception fileExp) {
      // do nothing
    }

    return new ValidationInfo(DvcsBundle.getString("clone.repository.url.error.invalid"), myRepositoryUrlCombobox);
  }

  /**
   * Check destination directory and set appropriate error text if there are problems
   *
   * @return null if destination directory is OK.
   */
  @Nullable
  private ValidationInfo checkDirectory() {
    String directoryPath = myDirectoryField.getText();
    if (directoryPath.length() == 0) {
      return new ValidationInfo("");
    }

    try {
      Path path = Paths.get(directoryPath);
      if (!Files.exists(path)) {
        return null;
      }
      else if (!Files.isDirectory(path)) {
        return new ValidationInfo(DvcsBundle.getString("clone.destination.directory.error.not.directory"), myDirectoryField.getTextField());
      }
      else if (!isDirectoryEmpty(path)) {
        return new ValidationInfo(DvcsBundle.message("clone.destination.directory.error.exists"), myDirectoryField.getTextField());
      }
    }
    catch (InvalidPathException | IOException e) {
      return new ValidationInfo(DvcsBundle.getString("clone.destination.directory.error.invalid"), myDirectoryField.getTextField());
    }
    return null;
  }

  private static boolean isDirectoryEmpty(@NotNull Path directory) throws IOException {
    DirectoryStream<Path> directoryStream = Files.newDirectoryStream(directory);
    return !directoryStream.iterator().hasNext();
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
    myRepositoryUrlCombobox.addItem(item);
  }

  public void rememberSettings() {
    final DvcsRememberedInputs rememberedInputs = getRememberedInputs();
    rememberedInputs.addUrl(getSourceRepositoryURL());
    rememberedInputs.setCloneParentDir(getParentDirectory());
  }

  @NotNull
  private static String safeUrlDecode(@NotNull String encoded) {
    try {
      return URLDecoder.decode(encoded, CharsetToolkit.UTF8);
    }
    catch (Exception e) {
      return encoded;
    }
  }

  /**
   * Get default name for checked out directory
   *
   * @param url an URL to checkout
   * @return a default repository name
   */
  @NotNull
  private String defaultDirectoryName(@NotNull final String url) {
    return stripSuffix(safeUrlDecode(getLastPathFragment(url)));
  }

  @NotNull
  private String stripSuffix(@NotNull String directoryName) {
    return directoryName.endsWith(myVcsDirectoryName)
           ? directoryName.substring(0, directoryName.length() - myVcsDirectoryName.length())
           : directoryName;
  }

  @NotNull
  private static String getLastPathFragment(@NotNull final String url) {
    // Suppose it's a URL
    int i = url.lastIndexOf('/');

    // No? Maybe win-style path?
    if (i == -1 && File.separatorChar != '/') i = url.lastIndexOf(File.separatorChar);

    if (i < 0) return "";

    if (i == url.length() - 1) return getLastPathFragment(url.substring(0, i));

    return url.substring(i + 1);
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
        protected void textChanged(DocumentEvent e) {
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

    public LoginButtonComponent(@NotNull List<Action> actions) {
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
          myButton.setOptions(null);
          myPanel.setVisible(false);
        }
      }
    }

    @NotNull
    private static Action[] getActionsAfterFirst(@NotNull List<Action> actions) {
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