// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;

import static java.awt.GridBagConstraints.*;

/**
 * @author Eugene Zhuravlev
 */
public class NamePathComponent extends JPanel {
  private static final Logger LOG = Logger.getInstance(NamePathComponent.class);

  private final JTextField myTfName;
  private final JTextField myTfPath;
  private final JLabel myNameLabel;
  private final FieldPanel myPathPanel;

  private boolean myIsNameChangedByUser = false;
  private boolean myIsPathChangedByUser = false;
  private boolean myIsPathNameSyncEnabled = true;
  private boolean myIsNamePathSyncEnabled = true;
  private boolean myShouldBeAbsolute;

  public NamePathComponent(String nameLabelText, String pathLabelText, String pathChooserTitle, String pathChooserDescription) {
    this(nameLabelText, pathLabelText, pathChooserTitle, pathChooserDescription, true);
  }

  public NamePathComponent(String nameLabelText,
                           String pathLabelText,
                           String pathChooserTitle,
                           String pathChooserDescription,
                           boolean hideIgnored) {
    this(nameLabelText, pathLabelText, pathChooserTitle, pathChooserDescription, hideIgnored, true);
  }

  public NamePathComponent(String nameLabelText,
                           String pathLabelText,
                           String pathChooserTitle,
                           String pathChooserDescription,
                           boolean hideIgnored,
                           boolean bold) {
    super(new GridBagLayout());

    myTfName = new JTextField();
    myTfName.setDocument(new NameFieldDocument());
    myTfName.setPreferredSize(new Dimension(200, myTfName.getPreferredSize().height));

    myTfPath = new JTextField();
    myTfPath.setDocument(new PathFieldDocument());
    myTfPath.setPreferredSize(new Dimension(200, myTfPath.getPreferredSize().height));

    myNameLabel = new JLabel(nameLabelText);
    if (bold) myNameLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
    myNameLabel.setLabelFor(myTfName);

    FileChooserDescriptor chooserDescriptor = (FileChooserDescriptor)BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR.clone();
    chooserDescriptor.setHideIgnored(hideIgnored);
    BrowseFilesListener browseButtonActionListener = new BrowseFilesListener(myTfPath, pathChooserTitle, pathChooserDescription, chooserDescriptor) {
      @Override
      public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        myIsPathChangedByUser = true;
      }
    };
    myPathPanel = new FieldPanel(myTfPath, null, null, browseButtonActionListener, null);

    JLabel pathLabel = new JLabel(pathLabelText);
    if (bold) pathLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
    pathLabel.setLabelFor(myTfPath);

    add(myNameLabel, new GridBagConstraints(0, RELATIVE, 1, 1, 0.0, 0.0, WEST, NONE, JBUI.insets(0, 0, 5, 4), 0, 0));
    add(myTfName, new GridBagConstraints(1, RELATIVE, 1, 1, 1.0, 0.0, NORTHWEST, HORIZONTAL, JBUI.insetsBottom(5), 0, 0));
    add(pathLabel, new GridBagConstraints(0, RELATIVE, 1, 1, 0.0, 0.0, WEST, NONE, JBUI.insets(0, 0, 5, 4), 0, 0));
    add(myPathPanel, new GridBagConstraints(1, RELATIVE, 1, 1, 1.0, 0.0, NORTHWEST, HORIZONTAL, JBUI.insetsBottom(5), 0, 0));
  }

  public static NamePathComponent initNamePathComponent(WizardContext context) {
    NamePathComponent component = new NamePathComponent(
      IdeBundle.message("label.project.name"),
      IdeBundle.message("label.project.files.location"),
      IdeBundle.message("title.select.project.file.directory", IdeBundle.message("project.new.wizard.project.identification")),
      IdeBundle.message("description.select.project.file.directory", StringUtil.capitalize(IdeBundle.message("project.new.wizard.project.identification"))),
      true, false
    );
    String baseDir = context.getProjectFileDirectory();
    String projectName = context.getProjectName();
    String initialProjectName = projectName != null ? projectName : ProjectWizardUtil.findNonExistingFileName(baseDir, "untitled", "");
    component.setPath(projectName == null ? (baseDir + File.separator + initialProjectName) : baseDir);
    component.setNameValue(initialProjectName);
    component.getNameComponent().select(0, initialProjectName.length());
    return component;
  }

  public boolean validateNameAndPath(WizardContext context, boolean defaultFormat) throws ConfigurationException {
    String name = getNameValue();
    if (StringUtil.isEmptyOrSpaces(name)) {
      ApplicationNamesInfo info = ApplicationNamesInfo.getInstance();
      throw new ConfigurationException(IdeBundle.message("prompt.new.project.file.name", info.getFullProductName(), context.getPresentationName()));
    }

    String projectDirectory = getPath();
    if (StringUtil.isEmptyOrSpaces(projectDirectory)) {
      throw new ConfigurationException(IdeBundle.message("prompt.enter.project.file.location", context.getPresentationName()));
    }
    if (myShouldBeAbsolute && !new File(projectDirectory).isAbsolute()) {
      throw new ConfigurationException(StringUtil.capitalize(IdeBundle.message("file.location.should.be.absolute", context.getPresentationName())));
    }

    boolean shouldPromptCreation = isPathChangedByUser();
    String message = IdeBundle.message("directory.project.file.directory", context.getPresentationName());
    if (!ProjectWizardUtil.createDirectoryIfNotExists(message, projectDirectory, shouldPromptCreation)) {
      return false;
    }

    File file = new File(projectDirectory);
    if (file.exists() && !file.canWrite()) {
      throw new ConfigurationException(String.format("Directory '%s' is not seem to be writable. Please consider another location.", projectDirectory));
    }
    for (Project p : ProjectManager.getInstance().getOpenProjects()) {
      if (ProjectUtil.isSameProject(projectDirectory, p)) {
        throw new ConfigurationException(String.format("Directory '%s' is already taken by the project '%s'. Please consider another location.", projectDirectory, p.getName()));
      }
    }

    boolean shouldContinue = true;
    String fileName = defaultFormat ? name + ProjectFileType.DOT_DEFAULT_EXTENSION : Project.DIRECTORY_STORE_FOLDER;
    File projectFile = new File(file, fileName);
    if (projectFile.exists()) {
      message = IdeBundle.message("prompt.overwrite.project.file", projectFile.getAbsolutePath(), context.getPresentationName());
      int answer = Messages.showYesNoDialog(message, IdeBundle.message("title.file.already.exists"), Messages.getQuestionIcon());
      shouldContinue = (answer == Messages.YES);
    }
    return shouldContinue;
  }

  public String getNameValue() {
    return myTfName.getText().trim();
  }

  public void setNameValue(String name) {
    boolean isNameChangedByUser = myIsNameChangedByUser;
    myIsNamePathSyncEnabled = false;
    try {
      myTfName.setText(name);
    }
    finally {
      myIsNameChangedByUser = isNameChangedByUser;
      myIsNamePathSyncEnabled = true;
    }
  }

  public String getPath() {
    String text = myTfPath.getText().trim();
    return FileUtil.toCanonicalPath(FileUtil.expandUserHome(text));
  }

  public void setPath(String path) {
    boolean isPathChangedByUser = myIsPathChangedByUser;
    myIsPathNameSyncEnabled = false;
    try {
      myTfPath.setText(FileUtil.getLocationRelativeToUserHome(FileUtil.toSystemDependentName(path)));
    }
    finally {
      myIsPathChangedByUser = isPathChangedByUser;
      myIsPathNameSyncEnabled = true;
    }
  }

  @NotNull
  public JTextField getNameComponent() {
    return myTfName;
  }

  public void setNameComponentVisible(boolean visible) {
    myTfName.setVisible(visible);
    myNameLabel.setVisible(visible);
  }

  @NotNull
  public JTextField getPathComponent() {
    return myTfPath;
  }

  @NotNull
  public FieldPanel getPathPanel() {
    return myPathPanel;
  }

  public boolean isNameChangedByUser() {
    return myIsNameChangedByUser;
  }

  public boolean isPathChangedByUser() {
    return myIsPathChangedByUser;
  }

  public void addChangeListener(Runnable callback) {
    DocumentAdapter adapter = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        callback.run();
      }
    };
    myTfName.getDocument().addDocumentListener(adapter);
    myTfPath.getDocument().addDocumentListener(adapter);
  }

  public void setShouldBeAbsolute(boolean shouldBeAbsolute) {
    myShouldBeAbsolute = shouldBeAbsolute;
  }

  private class NameFieldDocument extends PlainDocument {
    public NameFieldDocument() {
      addDocumentListener(new DocumentAdapter() {
        @Override
        public void textChanged(DocumentEvent event) {
          myIsNameChangedByUser = true;
          syncNameAndPath();
        }
      });
    }

    @Override
    public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
      boolean ok = true;
      for (int idx = 0; idx < str.length() && ok; idx++) {
        char ch = str.charAt(idx);
        ok = ch != File.separatorChar && ch != '\\' && ch != '/' && ch != '|' && ch != ':';
      }
      if (ok) {
        super.insertString(offs, str, a);
      }
    }

    private void syncNameAndPath() {
      if (myIsNamePathSyncEnabled && !isPathChangedByUser()) {
        try {
          myIsPathNameSyncEnabled = false;
          String name = getText(0, getLength());
          String path = myTfPath.getText().trim();
          int lastSeparatorIndex = path.lastIndexOf(File.separator);
          if (lastSeparatorIndex >= 0) {
            setPath(path.substring(0, lastSeparatorIndex + 1) + name);
          }
          else if (!path.isEmpty()) {
            setPath(path + File.separatorChar + name);
          }
        }
        catch (BadLocationException e) {
          LOG.error(e);
        }
        finally {
          myIsPathNameSyncEnabled = true;
        }
      }
    }
  }

  private class PathFieldDocument extends PlainDocument {
    public PathFieldDocument() {
      addDocumentListener(new DocumentAdapter() {
        @Override
        public void textChanged(DocumentEvent event) {
          myIsPathChangedByUser = true;
          syncPathAndName();
        }
      });
    }

    private void syncPathAndName() {
      if (myIsPathNameSyncEnabled && !isNameChangedByUser()) {
        try {
          myIsNamePathSyncEnabled = false;
          String path = getText(0, getLength());
          int lastSeparatorIndex = path.lastIndexOf(File.separator);
          if (lastSeparatorIndex >= 0 && (lastSeparatorIndex + 1) < path.length()) {
            setNameValue(path.substring(lastSeparatorIndex + 1));
          }
        }
        catch (BadLocationException e) {
          LOG.error(e);
        }
        finally {
          myIsNamePathSyncEnabled = true;
        }
      }
    }
  }
}