/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.application;

import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * @author max
 */
public class ImportOldConfigsPanel extends JDialog {
  private TextFieldWithBrowseButton myPrevInstallation;
  private JRadioButton myRbDoNotImport;
  private JRadioButton myRbImport;
  private JPanel myRootPanel;
  private File myLastSelection = null;
  private JButton myOkButton;

  public ImportOldConfigsPanel() {
    super(JOptionPane.getRootFrame(), true);

    new MnemonicHelper().register(getContentPane());

    ButtonGroup group = new ButtonGroup();
    group.add(myRbDoNotImport);
    group.add(myRbImport);
    myRbDoNotImport.setSelected(true);

    myRbImport.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        update();
      }
    });

    if (SystemInfo.isMac) {
      //noinspection HardCodedStringLiteral
      final String mostProbable = "/Applications/" + ApplicationNamesInfo.getInstance().getFullProductName();
      if (new File(mostProbable).exists()) {
        myPrevInstallation.setText(mostProbable);
      }
      else {
        //noinspection HardCodedStringLiteral
        myPrevInstallation.setText("/Applications");
      }
    }

    myPrevInstallation.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JFileChooser fc = new JFileChooser();
        if (myLastSelection != null){
          fc = new JFileChooser(myLastSelection);
        }

        if (!SystemInfo.isMac) {
          fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        }

        int returnVal = fc.showOpenDialog(ImportOldConfigsPanel.this);
        if (returnVal == JFileChooser.APPROVE_OPTION){
          File file = fc.getSelectedFile();
          if (file != null){
            myLastSelection = file;
            myPrevInstallation.setText(file.getAbsolutePath());
          }
        }
      }
    });

    myOkButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        close();
      }
    });

    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(myRootPanel);

    setTitle(ApplicationBundle.message("title.complete.installation"));

    update();
    pack();

    Dimension parentSize = Toolkit.getDefaultToolkit().getScreenSize();
    Dimension ownSize = getPreferredSize();

    setLocation((parentSize.width - ownSize.width) / 2, (parentSize.height - ownSize.height) / 2);
  }

  private void close() {
    if (isImportEnabled()) {
      final String productWithVendor = ApplicationNamesInfo.getInstance().getFullProductName();
      String instHome = myPrevInstallation.getText();
      if ("".equals(instHome)) {
        JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                      ApplicationBundle.message("error.please.select.previous.installation.home", productWithVendor),
                                      ApplicationBundle.message("title.installation.home.required"), JOptionPane.ERROR_MESSAGE);
        return;
      }

      if (PathManager.getHomePath().equals(instHome)) {
        JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                      ApplicationBundle.message("error.selected.current.installation.home",
                                                                productWithVendor, productWithVendor),
                                      ApplicationBundle.message("title.installation.home.required"), JOptionPane.ERROR_MESSAGE);
        return;
      }

      if (!ConfigImportHelper.isInstallationHome(instHome)) {
        JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                      ApplicationBundle.message("error.does.not.appear.to.be.installation.home", instHome,
                                                                productWithVendor),
                                      ApplicationBundle.message("title.installation.home.required"), JOptionPane.ERROR_MESSAGE);
        return;
      }

      if (!new File(instHome).canRead()) {
        JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                      ApplicationBundle.message("error.no.read.permissions", instHome),
                                      ApplicationBundle.message("title.installation.home.required"), JOptionPane.ERROR_MESSAGE);
        return;
      }
    }

    dispose();
  }

  public boolean isImportEnabled() {
    return myRbImport.isSelected();
  }

  public File getSelectedFile() {
    return new File(myPrevInstallation.getText());
  }

  private void update() {
    myPrevInstallation.setEnabled(myRbImport.isSelected());
  }

  public static void main(String[] args) {
    ImportOldConfigsPanel dlg = new ImportOldConfigsPanel();
    dlg.setVisible(true);
  }
}
