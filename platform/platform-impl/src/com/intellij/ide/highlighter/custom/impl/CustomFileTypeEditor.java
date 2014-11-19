/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author Yura Cangea, dsl
 */
public class CustomFileTypeEditor extends SettingsEditor<AbstractFileType> {
  private final JTextField myFileTypeName = new JTextField();
  private final JTextField myFileTypeDescr = new JTextField();
  private final JCheckBox myIgnoreCase = new JCheckBox(IdeBundle.message("checkbox.customfiletype.ignore.case"));
  private final JCheckBox mySupportBraces = new JCheckBox(IdeBundle.message("checkbox.customfiletype.support.paired.braces"));
  private final JCheckBox mySupportBrackets = new JCheckBox(IdeBundle.message("checkbox.customfiletype.support.paired.brackets"));
  private final JCheckBox mySupportParens = new JCheckBox(IdeBundle.message("checkbox.customfiletype.support.paired.parens"));
  private final JCheckBox mySupportEscapes = new JCheckBox(IdeBundle.message("checkbox.customfiletype.support.string.escapes"));

  private final JTextField myLineComment = new JTextField(5);
  private final JCheckBox myCommentAtLineStart = new JCheckBox(UIUtil.replaceMnemonicAmpersand("&Only at line start"));
  private final JTextField myBlockCommentStart = new JTextField(5);
  private final JTextField myBlockCommentEnd = new JTextField(5);
  private final JTextField myHexPrefix = new JTextField(5);

  private final JTextField myNumPostfixes = new JTextField(5);
  private final JBList[] myKeywordsLists = new JBList[]{new JBList(), new JBList(), new JBList(), new JBList()};
  private final DefaultListModel[] myKeywordModels =
    new DefaultListModel[]{new DefaultListModel(), new DefaultListModel(), new DefaultListModel(), new DefaultListModel()};

  public CustomFileTypeEditor() {
    myLineComment.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        boolean enabled = StringUtil.isNotEmpty(myLineComment.getText());
        myCommentAtLineStart.setEnabled(enabled);
        if (!enabled) {
          myCommentAtLineStart.setSelected(false);
        }
      }
    });
    myCommentAtLineStart.setEnabled(false);
  }

  @Override
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
      myCommentAtLineStart.setSelected(table.lineCommentOnlyAtStart);

      mySupportBraces.setSelected(table.isHasBraces());
      mySupportBrackets.setSelected(table.isHasBrackets());
      mySupportParens.setSelected(table.isHasParens());
      mySupportEscapes.setSelected(table.isHasStringEscapes());

      for (String s : table.getKeywords1()) {
        myKeywordModels[0].addElement(s);
      }
      for (String s : table.getKeywords2()) {
        myKeywordModels[1].addElement(s);
      }
      for (String s : table.getKeywords3()) {
        myKeywordModels[2].addElement(s);
      }
      for (String s : table.getKeywords4()) {
        myKeywordModels[3].addElement(s);
      }
    }
  }

  @Override
  public void applyEditorTo(AbstractFileType type) throws ConfigurationException {
    if (myFileTypeName.getText().trim().length() == 0) {
      throw new ConfigurationException(IdeBundle.message("error.name.cannot.be.empty"),
                                       CommonBundle.getErrorTitle());
    }
    else if (myFileTypeDescr.getText().trim().length() == 0) {
      myFileTypeDescr.setText(myFileTypeName.getText());
    }
    type.setName(myFileTypeName.getText());
    type.setDescription(myFileTypeDescr.getText());
    type.setSyntaxTable(getSyntaxTable());
  }

  @Override
  @NotNull
  public JComponent createEditor() {
    JComponent panel = createCenterPanel();
    for (int i = 0; i < myKeywordsLists.length; i++) {
      myKeywordsLists[i].setModel(myKeywordModels[i]);
    }
    return panel;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());

    JPanel fileTypePanel = new JPanel(new BorderLayout());
    JPanel info = FormBuilder.createFormBuilder()
      .addLabeledComponent(IdeBundle.message("editbox.customfiletype.name"), myFileTypeName)
      .addLabeledComponent(IdeBundle.message("editbox.customfiletype.description"), myFileTypeDescr).getPanel();
    info.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
    fileTypePanel.add(info, BorderLayout.NORTH);

    JPanel highlighterPanel = new JPanel();
    highlighterPanel.setBorder(IdeBorderFactory.createTitledBorder(IdeBundle.message("group.customfiletype.syntax.highlighting"), false));
    highlighterPanel.setLayout(new BorderLayout());
    JPanel commentsAndNumbersPanel = new JPanel();
    commentsAndNumbersPanel.setLayout(new GridBagLayout());

    JPanel _panel1 = new JPanel(new BorderLayout());
    GridBag gb = new GridBag()
      .setDefaultFill(GridBagConstraints.HORIZONTAL)
      .setDefaultAnchor(GridBagConstraints.WEST)
      .setDefaultInsets(1, 5, 1, 5);

    commentsAndNumbersPanel.add(new JLabel(IdeBundle.message("editbox.customfiletype.line.comment")), gb.nextLine().next());
    commentsAndNumbersPanel.add(myLineComment, gb.next());
    commentsAndNumbersPanel.add(myCommentAtLineStart, gb.next().coverLine(2));

    commentsAndNumbersPanel.add(new JLabel(IdeBundle.message("editbox.customfiletype.block.comment.start")), gb.nextLine().next());
    commentsAndNumbersPanel.add(myBlockCommentStart, gb.next());
    commentsAndNumbersPanel.add(new JLabel(IdeBundle.message("editbox.customfiletype.block.comment.end")), gb.next());
    commentsAndNumbersPanel.add(myBlockCommentEnd, gb.next());

    commentsAndNumbersPanel.add(new JLabel(IdeBundle.message("editbox.customfiletype.hex.prefix")), gb.nextLine().next());
    commentsAndNumbersPanel.add(myHexPrefix, gb.next());
    commentsAndNumbersPanel.add(new JLabel(IdeBundle.message("editbox.customfiletype.number.postfixes")), gb.next());
    commentsAndNumbersPanel.add(myNumPostfixes, gb.next());

    commentsAndNumbersPanel.add(mySupportBraces, gb.nextLine().next().coverLine(2));
    commentsAndNumbersPanel.add(mySupportBrackets, gb.next().next().coverLine(2));
    commentsAndNumbersPanel.add(mySupportParens, gb.nextLine().next().coverLine(2));
    commentsAndNumbersPanel.add(mySupportEscapes, gb.next().next().coverLine(2));

    _panel1.add(commentsAndNumbersPanel, BorderLayout.WEST);


    highlighterPanel.add(_panel1, BorderLayout.NORTH);

    TabbedPaneWrapper tabbedPaneWrapper = new TabbedPaneWrapper(this);
    tabbedPaneWrapper.getComponent().setBorder(IdeBorderFactory.createTitledBorder(IdeBundle.message("listbox.customfiletype.keywords"),
                                                                                   false));
    tabbedPaneWrapper.addTab(" 1 ", createKeywordsPanel(0));
    tabbedPaneWrapper.addTab(" 2 ", createKeywordsPanel(1));
    tabbedPaneWrapper.addTab(" 3 ", createKeywordsPanel(2));
    tabbedPaneWrapper.addTab(" 4 ", createKeywordsPanel(3));

    highlighterPanel.add(tabbedPaneWrapper.getComponent(), BorderLayout.CENTER);
    highlighterPanel.add(myIgnoreCase, BorderLayout.SOUTH);

    fileTypePanel.add(highlighterPanel, BorderLayout.CENTER);

    panel.add(fileTypePanel);

    for (int i = 0; i < myKeywordsLists.length; i++) {
      final int idx = i;
      new DoubleClickListener() {
        @Override
        protected boolean onDoubleClick(MouseEvent e) {
          edit(idx);
          return true;
        }
      }.installOn(myKeywordsLists[i]);
    }


    return panel;
  }

  private JPanel createKeywordsPanel(final int index) {
    JPanel panel = ToolbarDecorator.createDecorator(myKeywordsLists[index])
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          ModifyKeywordDialog dialog = new ModifyKeywordDialog(myKeywordsLists[index], "");
          if (dialog.showAndGet()) {
            String keywordName = dialog.getKeywordName();
            if (!myKeywordModels[index].contains(keywordName)) myKeywordModels[index].addElement(keywordName);
          }
        }
      }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          ListUtil.removeSelectedItems(myKeywordsLists[index]);
        }
      }).disableUpDownActions().createPanel();
    panel.setBorder(null);
    return panel;
  }

  private void edit(int index) {
    if (myKeywordsLists[index].getSelectedIndex() == -1) return;
    ModifyKeywordDialog dialog = new ModifyKeywordDialog(myKeywordsLists[index], (String)myKeywordsLists[index].getSelectedValue());
    if (dialog.showAndGet()) {
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
    syntaxTable.lineCommentOnlyAtStart = myCommentAtLineStart.isSelected();

    boolean ignoreCase = myIgnoreCase.isSelected();
    syntaxTable.setIgnoreCase(ignoreCase);

    syntaxTable.setHasBraces(mySupportBraces.isSelected());
    syntaxTable.setHasBrackets(mySupportBrackets.isSelected());
    syntaxTable.setHasParens(mySupportParens.isSelected());
    syntaxTable.setHasStringEscapes(mySupportEscapes.isSelected());

    for (int i = 0; i < myKeywordModels[0].size(); i++) {
      if (ignoreCase) {
        syntaxTable.addKeyword1(((String)myKeywordModels[0].getElementAt(i)).toLowerCase());
      }
      else {
        syntaxTable.addKeyword1((String)myKeywordModels[0].getElementAt(i));
      }
    }
    for (int i = 0; i < myKeywordModels[1].size(); i++) {
      if (ignoreCase) {
        syntaxTable.addKeyword2(((String)myKeywordModels[1].getElementAt(i)).toLowerCase());
      }
      else {
        syntaxTable.addKeyword2((String)myKeywordModels[1].getElementAt(i));
      }
    }
    for (int i = 0; i < myKeywordModels[2].size(); i++) {
      if (ignoreCase) {
        syntaxTable.addKeyword3(((String)myKeywordModels[2].getElementAt(i)).toLowerCase());
      }
      else {
        syntaxTable.addKeyword3((String)myKeywordModels[2].getElementAt(i));
      }
    }
    for (int i = 0; i < myKeywordModels[3].size(); i++) {
      if (ignoreCase) {
        syntaxTable.addKeyword4(((String)myKeywordModels[3].getElementAt(i)).toLowerCase());
      }
      else {
        syntaxTable.addKeyword4((String)myKeywordModels[3].getElementAt(i));
      }
    }
    return syntaxTable;
  }
}
