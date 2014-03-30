/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.EditorComboBox;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

/**
 * @author Nadya Zabrodina
 */
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

  private JPanel myRootPanel;
  private EditorComboBox myRepositoryURL;
  private TextFieldWithBrowseButton myParentDirectory;
  private JButton myTestButton; // test repository
  private JTextField myDirectoryName;

  @NotNull private String myTestURL; // the repository URL at the time of the last test
  @Nullable private Boolean myTestResult; // the test result of the last test or null if not tested
  @NotNull private String myDefaultDirectoryName = "";
  @NotNull protected final Project myProject;
  @NotNull protected final String myVcsDirectoryName;

  public CloneDvcsDialog(@NotNull Project project, @NotNull String vcsDirectoryName) {
    super(project, true);
    myProject = project;
    myVcsDirectoryName = vcsDirectoryName;
    init();
    initListeners();
    setTitle(DvcsBundle.getString("clone.title"));
    setOKButtonText(DvcsBundle.getString("clone.button"));
  }

  @NotNull
  public String getSourceRepositoryURL() {
    return getCurrentUrlText();
  }

  public String getParentDirectory() {
    return myParentDirectory.getText();
  }

  public String getDirectoryName() {
    return myDirectoryName.getText();
  }

  /**
   * Init components
   */
  private void initListeners() {
    FileChooserDescriptor fcd = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    fcd.setShowFileSystemRoots(true);
    fcd.setTitle(DvcsBundle.getString("clone.destination.directory.title"));
    fcd.setDescription(DvcsBundle.getString("clone.destination.directory.description"));
    fcd.setHideIgnored(false);
    myParentDirectory.addActionListener(
      new ComponentWithBrowseButton.BrowseFolderActionListener<JTextField>(fcd.getTitle(), fcd.getDescription(), myParentDirectory,
                                                                           myProject, fcd, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT) {
        @Override
        protected VirtualFile getInitialFile() {
          // suggest project base directory only if nothing is typed in the component.
          String text = getComponentText();
          if (text.length() == 0) {
            VirtualFile file = myProject.getBaseDir();
            if (file != null) {
              return file;
            }
          }
          return super.getInitialFile();
        }
      }
    );

    final DocumentListener updateOkButtonListener = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateButtons();
      }
    };
    myParentDirectory.getChildComponent().getDocument().addDocumentListener(updateOkButtonListener);
    String parentDir = getRememberedInputs().getCloneParentDir();
    if (StringUtil.isEmptyOrSpaces(parentDir)) {
      parentDir = ProjectUtil.getBaseDir();
    }
    myParentDirectory.setText(parentDir);

    myDirectoryName.getDocument().addDocumentListener(updateOkButtonListener);

    myTestButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        test();
      }
    });

    setOKActionEnabled(false);
    myTestButton.setEnabled(false);
  }

  private void test() {
    myTestURL = getCurrentUrlText();
    boolean testResult = test(myTestURL);

    if (testResult) {
      Messages.showInfoMessage(myTestButton, DvcsBundle.message("clone.test.success.message", myTestURL),
                               DvcsBundle.getString("clone.test.connection.title"));
      myTestResult = Boolean.TRUE;
    }
    else {
      myTestResult = Boolean.FALSE;
    }
    updateButtons();
  }

  protected abstract boolean test(@NotNull String url);

  @NotNull
  protected abstract DvcsRememberedInputs getRememberedInputs();

  /**
   * Check fields and display error in the wrapper if there is a problem
   */
  private void updateButtons() {
    if (!checkRepositoryURL()) {
      return;
    }
    if (!checkDestination()) {
      return;
    }
    setErrorText(null);
    setOKActionEnabled(true);
  }

  /**
   * Check destination directory and set appropriate error text if there are problems
   *
   * @return true if destination components are OK.
   */
  private boolean checkDestination() {
    if (myParentDirectory.getText().length() == 0 || myDirectoryName.getText().length() == 0) {
      setErrorText(null);
      setOKActionEnabled(false);
      return false;
    }
    File file = new File(myParentDirectory.getText(), myDirectoryName.getText());
    if (file.exists()) {
      setErrorText(DvcsBundle.message("clone.destination.exists.error", file));
      setOKActionEnabled(false);
      return false;
    }
    else if (!file.getParentFile().exists()) {
      setErrorText(DvcsBundle.message("clone.parent.missing.error", file.getParent()));
      setOKActionEnabled(false);
      return false;
    }
    return true;
  }

  /**
   * Check repository URL and set appropriate error text if there are problems
   *
   * @return true if repository URL is OK.
   */
  private boolean checkRepositoryURL() {
    String repository = getCurrentUrlText();
    if (repository.length() == 0) {
      setErrorText(null);
      setOKActionEnabled(false);
      return false;
    }
    if (myTestResult != null && repository.equals(myTestURL)) {
      if (!myTestResult.booleanValue()) {
        setErrorText(DvcsBundle.getString("clone.test.failed.error"));
        setOKActionEnabled(false);
        return false;
      }
      else {
        return true;
      }
    }
    try {
      if (new URI(repository).isAbsolute()) {
        return true;
      }
    }
    catch (URISyntaxException urlExp) {
      // do nothing
    }
    // check if ssh url pattern
    if (SSH_URL_PATTERN.matcher(repository).matches()) {
      return true;
    }
    try {
      File file = new File(repository);
      if (file.exists()) {
        if (!file.isDirectory()) {
          setErrorText(DvcsBundle.getString("clone.url.is.not.directory.error"));
          setOKActionEnabled(false);
        }
        return true;
      }
    }
    catch (Exception fileExp) {
      // do nothing
    }
    setErrorText(DvcsBundle.getString("clone.invalid.url"));
    setOKActionEnabled(false);
    return false;
  }

  @NotNull
  private String getCurrentUrlText() {
    return myRepositoryURL.getText().trim();
  }

  private void createUIComponents() {
    myRepositoryURL = new EditorComboBox("");
    final DvcsRememberedInputs rememberedInputs = getRememberedInputs();
    myRepositoryURL.setHistory(ArrayUtil.toObjectArray(rememberedInputs.getVisitedUrls(), String.class));
    myRepositoryURL.addDocumentListener(new com.intellij.openapi.editor.event.DocumentAdapter() {
      @Override
      public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent e) {
        // enable test button only if something is entered in repository URL
        final String url = getCurrentUrlText();
        myTestButton.setEnabled(url.length() != 0);
        if (myDefaultDirectoryName.equals(myDirectoryName.getText()) || myDirectoryName.getText().length() == 0) {
          // modify field if it was unmodified or blank
          myDefaultDirectoryName = defaultDirectoryName(url, myVcsDirectoryName);
          myDirectoryName.setText(myDefaultDirectoryName);
        }
        updateButtons();
      }
    });
  }

  public void prependToHistory(@NotNull final String item) {
    myRepositoryURL.prependItem(item);
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
  private static String defaultDirectoryName(@NotNull final String url, @NotNull final String vcsDirName) {
    String nonSystemName;
    if (url.endsWith("/" + vcsDirName) || url.endsWith(File.separator + vcsDirName)) {
      nonSystemName = url.substring(0, url.length() - vcsDirName.length() - 1);
    }
    else {
      if (url.endsWith(vcsDirName)) {
        nonSystemName = url.substring(0, url.length() - vcsDirName.length());
      }
      else {
        nonSystemName = url;
      }
    }
    int i = nonSystemName.lastIndexOf('/');
    if (i == -1 && File.separatorChar != '/') {
      i = nonSystemName.lastIndexOf(File.separatorChar);
    }
    return i >= 0 ? nonSystemName.substring(i + 1) : "";
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myRepositoryURL;
  }

  protected JComponent createCenterPanel() {
    return myRootPanel;
  }
}
