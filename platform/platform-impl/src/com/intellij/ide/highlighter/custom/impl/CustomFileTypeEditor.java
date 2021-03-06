// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.highlighter.custom.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Stream;

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
  private final JCheckBox myCommentAtLineStart = new JCheckBox(UIUtil.replaceMnemonicAmpersand(IdeBundle.message("only.at.line.start")));
  private final JTextField myBlockCommentStart = new JTextField(5);
  private final JTextField myBlockCommentEnd = new JTextField(5);
  private final JTextField myHexPrefix = new JTextField(5);

  private final JTextField myNumPostfixes = new JTextField(5);
  private final JTextArea[] myKeywordsLists = {new JTextArea(), new JTextArea(), new JTextArea(), new JTextArea()};

  public CustomFileTypeEditor() {
    myLineComment.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
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
  public void resetEditorFrom(@NotNull AbstractFileType fileType) {
    myFileTypeName.setText(fileType.getName());
    myFileTypeDescr.setText(fileType.getDescription());

    SyntaxTable table = fileType.getSyntaxTable();

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

    myKeywordsLists[0].setText(StreamEx.of(table.getKeywords1()).sorted().joining("\n"));
    myKeywordsLists[1].setText(StreamEx.of(table.getKeywords2()).sorted().joining("\n"));
    myKeywordsLists[2].setText(StreamEx.of(table.getKeywords3()).sorted().joining("\n"));
    myKeywordsLists[3].setText(StreamEx.of(table.getKeywords4()).sorted().joining("\n"));
    for (int i = 0; i < 4; i++) {
      myKeywordsLists[i].setCaretPosition(0);
    }
  }

  @Override
  public void applyEditorTo(@NotNull AbstractFileType type) throws ConfigurationException {
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
    for (int i = 0; i < 4; i++) {
      JTextArea list = myKeywordsLists[i];
      list.setRows(10);
      tabbedPaneWrapper.addTab(" " + (i + 1) + " ", new JBScrollPane(list));
    }

    highlighterPanel.add(tabbedPaneWrapper.getComponent(), BorderLayout.CENTER);
    highlighterPanel.add(myIgnoreCase, BorderLayout.SOUTH);

    fileTypePanel.add(highlighterPanel, BorderLayout.CENTER);

    panel.add(fileTypePanel);

    return panel;
  }

  @NotNull
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

    splitKeywordLines(ignoreCase, myKeywordsLists[0]).forEach(syntaxTable::addKeyword1);
    splitKeywordLines(ignoreCase, myKeywordsLists[1]).forEach(syntaxTable::addKeyword2);
    splitKeywordLines(ignoreCase, myKeywordsLists[2]).forEach(syntaxTable::addKeyword3);
    splitKeywordLines(ignoreCase, myKeywordsLists[3]).forEach(syntaxTable::addKeyword4);

    return syntaxTable;
  }

  private static Stream<String> splitKeywordLines(boolean ignoreCase, JTextArea list) {
    return Arrays.stream(StringUtil.splitByLines(list.getText())).map(s -> ignoreCase ? s.toLowerCase(Locale.getDefault()) : s);
  }
}
