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

import com.intellij.dvcs.DvcsRememberedInputs;
import com.intellij.dvcs.hosting.RepositoryHostingService;
import com.intellij.dvcs.hosting.RepositoryListLoader;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.CoreProgressManager;
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
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
  private EditorTextField myRepositoryUrlField;
  private ComponentVisibilityProgressManager mySpinnerProgressManager;
  private JButton myTestButton; // test repository
  private MyTextFieldWithBrowseButton myDirectoryField;
  private LoginButtonComponent myLoginButtonComponent;

  @NotNull protected final Project myProject;
  @NotNull protected final String myVcsDirectoryName;

  @Nullable private ValidationInfo myCreateDirectoryValidationInfo;
  @Nullable private ValidationInfo myRepositoryTestValidationInfo;
  @Nullable private ProgressIndicator myRepositoryTestProgressIndicator;

  @NotNull private final List<String> myAvailableRepositories;

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
    myAvailableRepositories = new ArrayList<>();

    initComponents(defaultUrl);
    initUrlAutocomplete();
    setTitle(DvcsBundle.getString("clone.title"));
    setOKButtonText(DvcsBundle.getString("clone.button"));
    init();
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
    DvcsRememberedInputs rememberedInputs = getRememberedInputs();
    String parentDirectory = rememberedInputs.getCloneParentDir();

    myRepositoryUrlField = TextFieldWithAutoCompletion.create(myProject,
                                                              myAvailableRepositories,
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

    boolean defaultAlreadyAdded = false;
    for (String url : rememberedInputs.getVisitedUrls()) {
      myRepositoryUrlCombobox.addItem(url);
      if (defaultUrl != null) {
        defaultAlreadyAdded = defaultUrl.equalsIgnoreCase(url);
      }
    }
    if (defaultUrl != null && !defaultAlreadyAdded) {
      myRepositoryUrlCombobox.addItem(defaultUrl);
      myRepositoryUrlField.setText(defaultUrl);
    }
    myTestButton.setEnabled(!getCurrentUrlText().isEmpty());
  }

  private void initUrlAutocomplete() {
    List<Action> loginActions = new ArrayList<>();
    for (RepositoryHostingService service : getRepositoryHostingServices()) {
      RepositoryListLoader loader = service.getRepositoryListLoader(myProject);
      if (loader.isEnabled()) {
        ApplicationManager.getApplication().invokeLater(() -> schedule(loader), ModalityState.stateForComponent(getRootPane()));
      }
      else {
        loginActions.add(new AbstractAction(DvcsBundle.message("clone.repository.url.autocomplete.login.text",
                                                               service.getServiceDisplayName())) {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (loader.enable()) {
              myLoginButtonComponent.removeAction(this);
              schedule(loader);
            }
          }
        });
      }
    }

    myLoginButtonComponent = new LoginButtonComponent(loginActions);
  }

  @NotNull
  protected Collection<RepositoryHostingService> getRepositoryHostingServices() {
    return Collections.emptyList();
  }

  private void schedule(@NotNull RepositoryListLoader loader) {
    mySpinnerProgressManager.run(new Task.Backgroundable(myProject, "Not Visible") {
      private List<String> myLoadedRepositories;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        myLoadedRepositories = loader.getAvailableRepositories(indicator);
      }

      @Override
      public void onSuccess() {
        myAvailableRepositories.addAll(myLoadedRepositories);
      }

      @Override
      public void onThrowable(@NotNull Throwable error) {
        //TODO: show warning
      }
    });
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
    return myRepositoryUrlCombobox;
  }

  @NotNull
  @Override
  protected JPanel createSouthAdditionalPanel() {
    return myLoginButtonComponent.getPanel();
  }

  @NotNull
  protected JComponent createCenterPanel() {
    return PanelFactory.grid()
                       .add(PanelFactory.panel(JBUI.Panels.simplePanel(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP)
                                                          .addToCenter(myRepositoryUrlCombobox)
                                                          .addToRight(myTestButton))
                                        .withLabel(DvcsBundle.getString("clone.repository.url.label")))
                       .add(PanelFactory.panel(myDirectoryField)
                                        .withLabel(DvcsBundle.getString("clone.destination.directory.label")))
                       .createPanel();
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
        setText(myDefaultParentPath.resolve(child).toString());
        myModifiedByUser = false;
      }
    }
  }

  private static class ComponentVisibilityProgressManager implements Disposable {
    @NotNull private final JComponent myProgressDisplayComponent;
    @NotNull private final List<ProgressIndicator> myIndicators;

    public ComponentVisibilityProgressManager(@NotNull JComponent progressDisplayComponent) {
      myProgressDisplayComponent = progressDisplayComponent;
      myIndicators = new ArrayList<>();
    }

    @CalledInAwt
    public ProgressIndicator run(@NotNull Task.Backgroundable task) {
      ProgressIndicator indicator = new EmptyProgressIndicator(ModalityState.stateForComponent(myProgressDisplayComponent));
      myIndicators.add(indicator);
      myProgressDisplayComponent.setVisible(true);
      ((CoreProgressManager)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(task, indicator, () ->
        ApplicationManager.getApplication().invokeLater(() -> {
          myIndicators.remove(indicator);
          myProgressDisplayComponent.setVisible(!myIndicators.isEmpty());
        }, indicator.getModalityState()));
      return indicator;
    }

    @Override
    public void dispose() {
      for (ProgressIndicator indicator : myIndicators) {
        indicator.cancel();
      }
    }
  }

  private static class LoginButtonComponent {
    @NotNull private final JBOptionButton myButton;
    @NotNull private final JPanel myPanel;
    @NotNull private final List<Action> myActions;

    public LoginButtonComponent(@NotNull List<Action> actions) {
      myButton = new JBOptionButton(ContainerUtil.getFirstItem(actions), getActionsAfterFirst(actions));
      myPanel =
        PanelFactory.panel(myButton).withTooltip(DvcsBundle.getString("clone.repository.url.autocomplete.login.tooltip")).createPanel();
      myPanel.setVisible(!actions.isEmpty());
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