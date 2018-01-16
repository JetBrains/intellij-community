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
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.TextFieldWithHistory;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.file.*;
import java.util.ArrayList;
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

  private TextFieldWithHistory myRepositoryUrlField;
  private JButton myTestButton; // test repository
  private MyTextFieldWithBrowseButton myDirectoryField;

  @NotNull protected final Project myProject;
  @NotNull protected final String myVcsDirectoryName;

  @Nullable private ValidationInfo myCreateDirectoryValidationInfo;

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

    initComponents(defaultUrl);
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

    myRepositoryUrlField = new TextFieldWithHistory();
    myRepositoryUrlField.setHistory(rememberedInputs.getVisitedUrls());
    myRepositoryUrlField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        startTrackingValidation();
      }
    });
    myRepositoryUrlField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        myDirectoryField.trySetChildPath(defaultDirectoryName(myRepositoryUrlField.getText().trim()));
      }
    });

    myTestButton = new JButton(DvcsBundle.getString("clone.test"));
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
      myRepositoryUrlField.setTextAndAddToHistory(defaultUrl);
    }
    else if (!myRepositoryUrlField.getHistory().isEmpty()) {
      myRepositoryUrlField.setSelectedIndex(0);
    }
    myTestButton.setEnabled(!getCurrentUrlText().isEmpty());
  }

  private void test() {
    String testUrl = getCurrentUrlText();
    TestResult testResult = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> test(testUrl), DvcsBundle.message("clone.testing", testUrl), true, myProject);
    if (testResult.isSuccess()) {
      Messages.showInfoMessage(myTestButton, DvcsBundle.message("clone.test.success.message", testUrl),
                               DvcsBundle.getString("clone.test.connection.title"));
    }
    else {
      Messages.showErrorDialog(myProject, ObjectUtils.assertNotNull(testResult.getError()), "Repository Test Failed");
    }
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
      return new ValidationInfo(DvcsBundle.getString("clone.repository.url.error.empty"), myRepositoryUrlField);
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
          return new ValidationInfo(DvcsBundle.getString("clone.repository.url.error.not.directory"), myRepositoryUrlField);
        }
        return null;
      }
    }
    catch (Exception fileExp) {
      // do nothing
    }

    return new ValidationInfo(DvcsBundle.getString("clone.repository.url.error.invalid"), myRepositoryUrlField);
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

  public void prependToHistory(@NotNull final String item) {
    List<String> history = myRepositoryUrlField.getHistory();
    history.add(item);
    myRepositoryUrlField.setHistory(history);
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
  protected JComponent createCenterPanel() {
    return PanelFactory.grid()
                       .add(PanelFactory.panel(JBUI.Panels.simplePanel(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP)
                                                          .addToCenter(myRepositoryUrlField)
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
}