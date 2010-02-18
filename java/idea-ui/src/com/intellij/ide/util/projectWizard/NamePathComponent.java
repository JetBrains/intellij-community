/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 30, 2003
 */
public class NamePathComponent extends JPanel{
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.projectWizard.NamePathComponent");

  private JTextField myTfName;
  private JTextField myTfPath;
  private boolean myIsNameChangedByUser = false;
  private boolean myIsPathChangedByUser = false;
  private boolean myIsPathNameSyncEnabled = true;
  private boolean myIsNamePathSyncEnabled = true;
  private boolean myIsSyncEnabled = true;

  private FieldPanel myPathPanel;
  private JLabel myNameLabel;
  private boolean myForceSync;

  public NamePathComponent(String nameLabelText, String pathLabelText, char nameMnemonic, char locationMnemonic, final String pathChooserTitle, final String pathChooserDescription) {
    this(nameLabelText, pathLabelText, pathChooserTitle, pathChooserDescription, true);
  }

  public NamePathComponent(String nameLabelText,
                           String pathLabelText,
                           final String pathChooserTitle,
                           final String pathChooserDescription,
                           boolean hideIgnored) {
    this(nameLabelText, pathLabelText, pathChooserTitle, pathChooserDescription, hideIgnored, true);
  }

  public NamePathComponent(String nameLabelText,
                           String pathLabelText,
                           final String pathChooserTitle,
                           final String pathChooserDescription,
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
    this.add(myNameLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));

    this.add(myTfName, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(5, 0, 8, 0), 0, 0));
    // todo: review texts
    final FileChooserDescriptor chooserDescriptor = (FileChooserDescriptor)BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR.clone();
    chooserDescriptor.setHideIgnored(hideIgnored);
    final BrowseFilesListener browseButtonActionListener = new BrowseFilesListener(myTfPath, pathChooserTitle, pathChooserDescription, chooserDescriptor) {
      public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        myIsPathChangedByUser = true;
      }
    };
    myPathPanel = new FieldPanel(myTfPath, pathLabelText, null, browseButtonActionListener, null);
    final JLabel locationLabel = myPathPanel.getFieldLabel();
    locationLabel.setLabelFor(myTfPath);
    if (bold) locationLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
    this.add(myPathPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(8, 0, 0, 0), 0, 0));
  }

  public String getNameValue() {
    return myTfName.getText().trim();
  }

  public void setNameValue(String name) {
    final boolean isNameChangedByUser = myIsNameChangedByUser;
    setNamePathSyncEnabled(false);
    try {
      myTfName.setText(name);
    }
    finally {
      myIsNameChangedByUser = isNameChangedByUser;
      setNamePathSyncEnabled(true);
    }
  }

  public String getPath() {
    return myTfPath.getText().trim().replace(File.separatorChar, '/');
  }

  public void setPath(String path) {
    final boolean isPathChangedByUser = myIsPathChangedByUser;
    setPathNameSyncEnabled(false);
    try {
      myTfPath.setText(path);
    }
    finally {
      myIsPathChangedByUser = isPathChangedByUser;
      setPathNameSyncEnabled(true);
    }
  }

  public JTextField getNameComponent() {
    return myTfName;
  }

  public JTextField getPathComponent() {
    return myTfPath;
  }

  public void setPathComponentVisible(boolean visible) {
    myPathPanel.setVisible(visible);
  }

  public void setNameComponentVisible(boolean visible) {
    myTfName.setVisible(visible);
    myNameLabel.setVisible(visible);
  }

  public boolean isNameChangedByUser() {
    return myIsNameChangedByUser;
  }

  public boolean isPathChangedByUser() {
    return myIsPathChangedByUser;
  }

  public boolean isSyncEnabled() {
    return myIsSyncEnabled;
  }

  public void setSyncEnabled(boolean isSyncEnabled) {
    myIsSyncEnabled = isSyncEnabled;
  }

  private boolean isPathNameSyncEnabled() {
    if (!isSyncEnabled()) {
      return false;
    }
    return myIsPathNameSyncEnabled;
  }

  private void setPathNameSyncEnabled(boolean isPathNameSyncEnabled) {
    myIsPathNameSyncEnabled = isPathNameSyncEnabled;
  }

  private boolean isNamePathSyncEnabled() {
    if (!isSyncEnabled()) {
      return false;
    }
    return myIsNamePathSyncEnabled;
  }

  private void setNamePathSyncEnabled(boolean isNamePathSyncEnabled) {
    myIsNamePathSyncEnabled = isNamePathSyncEnabled;
  }

  public void syncNameToPath(boolean b) {
    myForceSync = b;
    if (b) ((PathFieldDocument)myTfPath.getDocument()).syncPathAndName();
  }

  private class NameFieldDocument extends PlainDocument {
    public NameFieldDocument() {
      addDocumentListener(new DocumentAdapter() {
        public void textChanged(DocumentEvent event) {
          myIsNameChangedByUser = true;
          syncNameAndPath();
        }
      });
    }

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
      if (isNamePathSyncEnabled() && (myForceSync || !myIsPathChangedByUser)) {
        try {
          setPathNameSyncEnabled(false);
          final String name = getText(0, getLength());
          final String path = myTfPath.getText().trim();
          final int lastSeparatorIndex = path.lastIndexOf(File.separator);
          if (lastSeparatorIndex >= 0) {
            setPath(path.substring(0, lastSeparatorIndex + 1) + name);
          }
        }
        catch (BadLocationException e) {
          LOG.error(e);
        }
        finally {
          setPathNameSyncEnabled(true);
        }
      }
    }
  }

  private class PathFieldDocument extends PlainDocument {
    public PathFieldDocument() {
      addDocumentListener(new DocumentAdapter() {
        public void textChanged(DocumentEvent event) {
          myIsPathChangedByUser = true;
          syncPathAndName();
        }
      });
    }

    private void syncPathAndName() {
      if (isPathNameSyncEnabled() && (myForceSync || !myIsNameChangedByUser)) {
        try {
          setNamePathSyncEnabled(false);
          final String path = getText(0, getLength());
          final int lastSeparatorIndex = path.lastIndexOf(File.separator);
          if (lastSeparatorIndex >= 0 && (lastSeparatorIndex + 1) < path.length()) {
            setNameValue(path.substring(lastSeparatorIndex + 1));
          }
        }
        catch (BadLocationException e) {
          LOG.error(e);
        }
        finally {
          setNamePathSyncEnabled(true);
        }
      }
    }
  }

}
