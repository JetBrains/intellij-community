// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// This is a slightly modified version of test 'tests.detailed.MainFrame' from repository https://github.com/JetBrains/jcef.git
package com.intellij.internal.jcef.test.detailed.dialog;

import org.cef.browser.CefBrowser;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@ApiStatus.Internal
public class  SearchDialog extends JDialog {
  private final CefBrowser browser_;
  private final JTextField searchField_ = new JTextField(30);
  private final JCheckBox caseCheckBox_ = new JCheckBox("Case sensitive");
  private final JButton prevButton_ = new JButton("Prev");
  private final JButton nextButton_ = new JButton("Next");

  public SearchDialog(Frame owner, CefBrowser browser) {
    super(owner, "Find...", false);
    browser_ = browser;

    setLayout(new BorderLayout());
    setSize(400, 100);

    JPanel searchPanel = new JPanel();
    searchPanel.setLayout(new BoxLayout(searchPanel, BoxLayout.X_AXIS));
    searchPanel.add(Box.createHorizontalStrut(5));
    searchPanel.add(new JLabel("Search:"));
    searchPanel.add(searchField_);

    JPanel controlPanel = new JPanel();
    controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.X_AXIS));
    controlPanel.add(Box.createHorizontalStrut(5));

    JButton searchButton = new JButton("Search");
    searchButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (searchField_.getText() == null || searchField_.getText().isEmpty()) return;

        setTitle("Find \"" + searchField_.getText() + "\"");
        boolean matchCase = caseCheckBox_.isSelected();
        browser_.find(searchField_.getText(), true, matchCase, false);
        prevButton_.setEnabled(true);
        nextButton_.setEnabled(true);
      }
    });
    controlPanel.add(searchButton);

    prevButton_.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean matchCase = caseCheckBox_.isSelected();
        setTitle("Find \"" + searchField_.getText() + "\"");
        browser_.find(searchField_.getText(), false, matchCase, true);
      }
    });
    prevButton_.setEnabled(false);
    controlPanel.add(prevButton_);

    nextButton_.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean matchCase = caseCheckBox_.isSelected();
        setTitle("Find \"" + searchField_.getText() + "\"");
        browser_.find(searchField_.getText(), true, matchCase, true);
      }
    });
    nextButton_.setEnabled(false);
    controlPanel.add(nextButton_);

    controlPanel.add(Box.createHorizontalStrut(50));

    JButton doneButton = new JButton("Done");
    doneButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        setVisible(false);
        dispose();
      }
    });
    controlPanel.add(doneButton);

    add(searchPanel, BorderLayout.NORTH);
    add(caseCheckBox_);
    add(controlPanel, BorderLayout.SOUTH);
  }

  @Override
  public void dispose() {
    browser_.stopFinding(true);
    super.dispose();
  }
}
