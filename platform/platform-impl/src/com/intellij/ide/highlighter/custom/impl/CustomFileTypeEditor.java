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
package com.intellij.ide.highlighter.custom.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.ui.DialogButtonGroup;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListUtil;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.components.JBList;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Iterator;

/**
 * @author Yura Cangea, dsl
 */
public class CustomFileTypeEditor extends SettingsEditor<AbstractFileType> {
  private final JTextField myFileTypeName = new JTextField(26);
  private final JTextField myFileTypeDescr = new JTextField(26);
  private final JCheckBox myIgnoreCase = new JCheckBox(IdeBundle.message("checkbox.customfiletype.ignore.case"));
  private final JCheckBox mySupportBraces = new JCheckBox(IdeBundle.message("checkbox.customfiletype.support.paired.braces"));
  private final JCheckBox mySupportBrackets = new JCheckBox(IdeBundle.message("checkbox.customfiletype.support.paired.brackets"));
  private final JCheckBox mySupportParens = new JCheckBox(IdeBundle.message("checkbox.customfiletype.support.paired.parens"));
  private final JCheckBox mySupportEscapes = new JCheckBox(IdeBundle.message("checkbox.customfiletype.support.string.escapes"));

  private final JTextField myLineComment = new JTextField(20);
  private final JTextField myBlockCommentStart = new JTextField(20);
  private final JTextField myBlockCommentEnd = new JTextField(20);
  private final JTextField myHexPrefix = new JTextField(20);
  private final JTextField myNumPostfixes = new JTextField(20);

  private final JList[] myKeywordsLists = new JList[]{new JBList(), new JBList(), new JBList(), new JBList()};
  private final DefaultListModel[] myKeywordModels = new DefaultListModel[]{new DefaultListModel(), new DefaultListModel(), new DefaultListModel(), new DefaultListModel()};
  private final JButton[] myAddKeywordButtons = new JButton[4];
  private final JButton[] myRemoveKeywordButtons = new JButton[4];

  public CustomFileTypeEditor() {
  }

  public void resetEditorFrom(AbstractFileType fileType) {
    myFileTypeName.setText(fileType.getName());
    myFileTypeDescr.setText(fileType.getDescription());

    SyntaxTable table = fileType.getSyntaxTable();

    if (table != null) {
      myLineComment.setText(table.getLineComment());
      myBlockCommentEnd.setText(table.getEndComment());
      myBlockCommentStart.setText(table.getStartComment());
      myHexPrefix.setText(table.getHexPrefix());
      myNumPostfixes.setText(table.getNumPostfixChars());
      myIgnoreCase.setSelected(table.isIgnoreCase());

      mySupportBraces.setSelected(table.isHasBraces());
      mySupportBrackets.setSelected(table.isHasBrackets());
      mySupportParens.setSelected(table.isHasParens());
      mySupportEscapes.setSelected(table.isHasStringEscapes());

      for (Iterator i = table.getKeywords1().iterator(); i.hasNext();) {
        myKeywordModels[0].addElement(i.next());
      }
      for (Iterator i = table.getKeywords2().iterator(); i.hasNext();) {
        myKeywordModels[1].addElement(i.next());
      }
      for (Iterator i = table.getKeywords3().iterator(); i.hasNext();) {
        myKeywordModels[2].addElement(i.next());
      }
      for (Iterator i = table.getKeywords4().iterator(); i.hasNext();) {
        myKeywordModels[3].addElement(i.next());
      }
    }
  }

  public void applyEditorTo(AbstractFileType type) throws ConfigurationException {
    if (myFileTypeName.getText().trim().length() == 0) {
      throw new ConfigurationException(IdeBundle.message("error.name.cannot.be.empty"),
                                       CommonBundle.getErrorTitle());
    } else if (myFileTypeDescr.getText().trim().length() == 0) {
      myFileTypeDescr.setText(myFileTypeName.getText());
    }
    type.setName(myFileTypeName.getText());
    type.setDescription(myFileTypeDescr.getText());
    type.setSyntaxTable(getSyntaxTable());
  }

  public JComponent createEditor() {
    JComponent panel = createCenterPanel();
    for (int i = 0; i < myKeywordsLists.length; i++) {
      myKeywordsLists[i].setModel(myKeywordModels[i]);
    }
    return panel;
  }

  public void disposeEditor() {
  }


  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());

    JPanel fileTypePanel = new JPanel(new BorderLayout());
    JPanel _panel0 = new JPanel(new BorderLayout());
    JPanel info = new JPanel(new GridBagLayout());
    GridBagConstraints gc = new GridBagConstraints();
    gc.gridx = 0;
    gc.gridy = 0;
    gc.anchor = GridBagConstraints.WEST;
    gc.fill = GridBagConstraints.HORIZONTAL;
    info.add(new JLabel(IdeBundle.message("editbox.customfiletype.name")), gc);
    gc.gridx = 1;
    gc.gridy = 0;
    info.add(myFileTypeName);
    gc.gridx = 0;
    gc.gridy = 2;
    info.add(new JLabel(IdeBundle.message("editbox.customfiletype.description")), gc);
    gc.gridx = 1;
    gc.gridy = 2;
    info.add(myFileTypeDescr, gc);
    gc.gridx = 0;
    gc.gridy = 3;
    _panel0.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    _panel0.add(info, BorderLayout.WEST);
    fileTypePanel.add(_panel0, BorderLayout.NORTH);

    JPanel panel1 = new JPanel();
    panel1.setBorder(IdeBorderFactory.createTitledBorder(IdeBundle.message("group.customfiletype.syntax.highlighting")));
    JPanel highlighterPanel = panel1;
    highlighterPanel.setLayout(new BorderLayout());
    JPanel panel2 = new JPanel();
    panel2.setBorder(IdeBorderFactory.createTitledBorder(IdeBundle.message("group.customfiletype.options")));
    JPanel commentsAndNumbersPanel = panel2;
    commentsAndNumbersPanel.setLayout(new GridBagLayout());

    JPanel _panel1 = new JPanel(new BorderLayout());
    gc = new GridBagConstraints();
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.anchor = GridBagConstraints.WEST;
    gc.gridx = 0;
    gc.gridy = 0;
    commentsAndNumbersPanel.add(new JLabel(IdeBundle.message("editbox.customfiletype.line.comment")), gc);
    gc.gridx = 1;
    commentsAndNumbersPanel.add(myLineComment, gc);
    gc.gridx = 0;
    gc.gridy++;
    commentsAndNumbersPanel.add(new JLabel(IdeBundle.message("editbox.customfiletype.block.comment.start")), gc);
    gc.gridx = 1;
    commentsAndNumbersPanel.add(myBlockCommentStart, gc);
    gc.gridx = 0;
    gc.gridy++;
    commentsAndNumbersPanel.add(new JLabel(IdeBundle.message("editbox.customfiletype.block.comment.end")), gc);
    gc.gridx = 1;
    commentsAndNumbersPanel.add(myBlockCommentEnd, gc);
    gc.gridx = 0;
    gc.gridy++;
    commentsAndNumbersPanel.add(new JLabel(IdeBundle.message("editbox.customfiletype.hex.prefix")), gc);
    gc.gridx = 1;
    commentsAndNumbersPanel.add(myHexPrefix, gc);
    gc.gridx = 0;
    gc.gridy++;
    commentsAndNumbersPanel.add(new JLabel(IdeBundle.message("editbox.customfiletype.number.postfixes")), gc);
    gc.gridx = 1;
    commentsAndNumbersPanel.add(myNumPostfixes, gc);

    gc.gridx = 0;
    gc.gridy++;
    commentsAndNumbersPanel.add(mySupportBraces, gc);

    gc.gridx = 0;
    gc.gridy++;
    commentsAndNumbersPanel.add(mySupportBrackets, gc);

    gc.gridx = 0;
    gc.gridy++;
    commentsAndNumbersPanel.add(mySupportParens, gc);

    gc.gridx = 0;
    gc.gridy++;
    commentsAndNumbersPanel.add(mySupportEscapes, gc);

    _panel1.add(commentsAndNumbersPanel, BorderLayout.WEST);


    highlighterPanel.add(_panel1, BorderLayout.NORTH);

    TabbedPaneWrapper tabbedPaneWrapper = new TabbedPaneWrapper(this);
    tabbedPaneWrapper.addTab("1", createKeywordsPanel(0));
    tabbedPaneWrapper.addTab("2", createKeywordsPanel(1));
    tabbedPaneWrapper.addTab("3", createKeywordsPanel(2));
    tabbedPaneWrapper.addTab("4", createKeywordsPanel(3));

    highlighterPanel.add(tabbedPaneWrapper.getComponent(), BorderLayout.CENTER);
    highlighterPanel.add(myIgnoreCase, BorderLayout.SOUTH);

    fileTypePanel.add(highlighterPanel, BorderLayout.CENTER);

    panel.add(fileTypePanel);

    for (int i = 0; i < myKeywordsLists.length; i++) {
      final int idx = i;
      myKeywordsLists[i].addMouseListener(new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2) edit(idx);
        }
      });
    }


    return panel;
  }

  private JPanel createKeywordsPanel(final int index) {
    JPanel panel = new JPanel();
    panel.setBorder(IdeBorderFactory.createTitledBorder(IdeBundle.message("listbox.customfiletype.keywords")));
    JPanel keywordsPanel = panel;
    keywordsPanel.setLayout(new BorderLayout());

    keywordsPanel.add(new JScrollPane(myKeywordsLists[index]), BorderLayout.CENTER);

    DialogButtonGroup buttonGroup = new DialogButtonGroup();
    buttonGroup.setBorder(BorderFactory.createEmptyBorder(0, 10, 5, 0));

    myAddKeywordButtons[index] = new JButton(IdeBundle.message("button.add"));
    myAddKeywordButtons[index].addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ModifyKeywordDialog dialog = new ModifyKeywordDialog(myAddKeywordButtons[index], "");
        dialog.show();
        if (dialog.isOK()) {
          String keywordName = dialog.getKeywordName();
          if (!myKeywordModels[index].contains(keywordName)) myKeywordModels[index].addElement(keywordName);
        }
      }
    });
    buttonGroup.addButton(myAddKeywordButtons[index]);

    myRemoveKeywordButtons[index] = new JButton(IdeBundle.message("button.remove"));
    myRemoveKeywordButtons[index].addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ListUtil.removeSelectedItems(myKeywordsLists[index]);
      }
    });

    buttonGroup.addButton(myRemoveKeywordButtons[index]);

    keywordsPanel.add(buttonGroup, BorderLayout.EAST);
    return keywordsPanel;
  }

  private void edit(int index) {
    if (myKeywordsLists[index].getSelectedIndex() == -1) return;
    ModifyKeywordDialog dialog = new ModifyKeywordDialog(myKeywordsLists[index], (String) myKeywordsLists[index].getSelectedValue());
    dialog.show();
    if (dialog.isOK()) {
      myKeywordModels[index].setElementAt(dialog.getKeywordName(), myKeywordsLists[index].getSelectedIndex());
    }
  }

  public SyntaxTable getSyntaxTable() {
    SyntaxTable syntaxTable = new SyntaxTable();
    syntaxTable.setLineComment(myLineComment.getText());
    syntaxTable.setStartComment(myBlockCommentStart.getText());
    syntaxTable.setEndComment(myBlockCommentEnd.getText());
    syntaxTable.setHexPrefix(myHexPrefix.getText());
    syntaxTable.setNumPostfixChars(myNumPostfixes.getText());

    boolean ignoreCase = myIgnoreCase.isSelected();
    syntaxTable.setIgnoreCase(ignoreCase);

    syntaxTable.setHasBraces(mySupportBraces.isSelected());
    syntaxTable.setHasBrackets(mySupportBrackets.isSelected());
    syntaxTable.setHasParens(mySupportParens.isSelected());
    syntaxTable.setHasStringEscapes(mySupportEscapes.isSelected());

    for (int i = 0; i < myKeywordModels[0].size(); i++) {
      if (ignoreCase) {
        syntaxTable.addKeyword1(((String) myKeywordModels[0].getElementAt(i)).toLowerCase());
      } else {
        syntaxTable.addKeyword1((String) myKeywordModels[0].getElementAt(i));
      }
    }
    for (int i = 0; i < myKeywordModels[1].size(); i++) {
      if (ignoreCase) {
        syntaxTable.addKeyword2(((String) myKeywordModels[1].getElementAt(i)).toLowerCase());
      } else {
        syntaxTable.addKeyword2((String) myKeywordModels[1].getElementAt(i));
      }
    }
    for (int i = 0; i < myKeywordModels[2].size(); i++) {
      if (ignoreCase) {
        syntaxTable.addKeyword3(((String) myKeywordModels[2].getElementAt(i)).toLowerCase());
      } else {
        syntaxTable.addKeyword3((String) myKeywordModels[2].getElementAt(i));
      }
    }
    for (int i = 0; i < myKeywordModels[3].size(); i++) {
      if (ignoreCase) {
        syntaxTable.addKeyword4(((String) myKeywordModels[3].getElementAt(i)).toLowerCase());
      } else {
        syntaxTable.addKeyword4((String) myKeywordModels[3].getElementAt(i));
      }
    }
    return syntaxTable;
  }
}
