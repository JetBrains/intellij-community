// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.fileTypes.FileTypesBundle;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.JBColor;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.valueEditors.TextFieldValueEditor;
import com.intellij.ui.components.fields.valueEditors.ValueEditor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

class IgnoredFilesAndFoldersPanel extends JPanel {

  private final DefaultListModel<String> myModel = new DefaultListModel<>();
  private final JBList<String> myPatternList;
  private final PatternEditField myEditField = new PatternEditField();

  IgnoredFilesAndFoldersPanel() {
    setLayout(new BorderLayout());
    myPatternList = new JBList<>();
    myPatternList.setModel(myModel);
    myPatternList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myPatternList)
                                                 .setScrollPaneBorder(JBUI.Borders.empty())
                                                 .setPanelBorder(JBUI.Borders.customLine(JBColor.border(),0,1,0,1))
                                                 .setAddAction(__ -> addPattern())
                                                 .setEditAction(__ -> editPattern())
                                                 .setRemoveAction(__ -> removePattern())
                                                 .disableUpDownActions();
    add(decorator.createPanel(), BorderLayout.NORTH);
    JPanel listPanel = new JPanel(new BorderLayout());
    myEditField.setVisible(false);
    listPanel.add(myEditField, BorderLayout.NORTH);
    JBScrollPane scrollPane = new JBScrollPane(myPatternList);
    listPanel.add(scrollPane, BorderLayout.CENTER);
    add(listPanel, BorderLayout.CENTER);
    JLabel label = new JLabel(FileTypesBundle.message("filetype.ignore.text"));
    label.setFont(JBUI.Fonts.smallFont());
    label.setForeground(JBColor.GRAY);
    label.setBorder(TitledSeparator.createEmptyBorder());
    add(label, BorderLayout.SOUTH);
  }

  private void removePattern() {
    int index = myPatternList.getSelectedIndex();
    if (index >= 0) {
      FileTypeConfigurableInteractions.ignorePatternRemoved.log();
      myModel.remove(index);
      if (myModel.size() > 0) {
        if (index >= myModel.size()) index = myModel.size() - 1;
        myPatternList.setSelectedIndex(index);
      }
    }
  }

  private void editPattern() {
    FileTypeConfigurableInteractions.ignorePatternEdited.log();
    myEditField.startEdit(myPatternList.getSelectedValue());
  }

  private void addPattern() {
    FileTypeConfigurableInteractions.ignorePatternAdded.log();
    myEditField.startEdit("");
  }

  @NotNull String getValues() {
    return String.join(";", ArrayUtil.toStringArray(Collections.list(myModel.elements())));
  }

  void setValues(@NotNull String values) {
    fillList(Arrays.asList(values.split(";")));
  }

  private void fillList(@NotNull List<String> patterns) {
    myModel.clear();
    myModel.addAll(patterns.stream().sorted().collect(Collectors.toList()));
  }

  private void reorderList() {
    int selected = myPatternList.getSelectedIndex();
    String selection = selected >= 0 ? myModel.get(selected) : null;
    fillList(Collections.list(myModel.elements()));
    if (selection != null) {
      int toSelect = myModel.indexOf(selection);
      if (toSelect >= 0) {
        myPatternList.setSelectedIndex(toSelect);
        myPatternList.ensureIndexIsVisible(toSelect);
      }
    }
  }

  private class PatternEditField extends JBTextField {
    private final         PatternValueEditor myValueEditor;
    private @Nls @NlsSafe String             myOldValue;

    private PatternEditField() {
      setBorder(true);
      myValueEditor = new PatternValueEditor(this);
      addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            if (trySave()) {
              stopEdit(e);
            }
            else {
              e.consume();
            }
          }
          else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            stopEdit(e);
          }
        }
      });
      myValueEditor.addListener(new ValueEditor.Listener<>() {
        @Override
        public void valueChanged(@NotNull String newValue) {
          setBorder(myValueEditor.isValid(newValue));
        }
      });
    }

    void setBorder(boolean isValid) {
      if (isValid) {
        setBorder(JBUI.Borders.customLine(JBColor.LIGHT_GRAY, 1, 1, 0, 1));
      }
      else {
        setBorder(JBUI.Borders.customLine(JBColor.RED));
      }
    }

    private boolean trySave() {
      String newValue = getText();
      if (newValue.equals(myOldValue)) {
        return true;
      }
      if (myValueEditor.isValid(newValue)) {
        if (myModel.contains(newValue)) {
          setBorder(false);
          showError(FileTypesBundle.message("filetype.ignore.error.already.exists"));
          return false;
        }
        if (myOldValue != null) {
          myModel.removeElement(myOldValue);
        }
        myModel.addElement(newValue);
        int index = myModel.indexOf(newValue);
        myPatternList.setSelectedIndex(index);
        setBorder(true);
        reorderList();
        return true;
      }
      else {
        showError(FileTypesBundle.message("filetype.ignore.error.invalid"));
        setBorder(false);
        return false;
      }
    }

    void startEdit(@NotNull @NlsSafe String value) {
      myOldValue = value.isEmpty() ? null : value;
      setText(value);
      setBorder(true);
      setVisible(true);
      requestFocus();
      myPatternList.setEnabled(false);
    }

    private void stopEdit(@NotNull KeyEvent event) {
      event.consume();
      setVisible(false);
      myPatternList.setEnabled(true);
      myPatternList.requestFocus();
    }

    private void showError(@NotNull @Nls String message) {
      Balloon balloon = JBPopupFactory.getInstance().createBalloonBuilder(new JLabel(message))
                                      .setFillColor(HintUtil.getErrorColor())
                                      .setHideOnKeyOutside(true)
                                      .setFadeoutTime(1000)
                                      .createBalloon();
      int xLoc = (int) getGraphics().getFontMetrics().getStringBounds(getText(), getGraphics()).getWidth();
      RelativePoint relativePoint =
        new RelativePoint(this, new Point(xLoc, this.getHeight()));
      balloon.show(relativePoint, Balloon.Position.below);
    }
  }

  private static class PatternValueEditor extends TextFieldValueEditor<String> {

    PatternValueEditor(@NotNull JTextField field) {
      super(field, FileTypesBundle.message("filetype.ignore.pattern"), "");
    }

    @Override
    public @NotNull String parseValue(@Nullable String text) throws InvalidDataException {
      if (text != null) {
        if (!isValid(text)) {
          throw new InvalidDataException(FileTypesBundle.message("filetype.ignore.error.invalid"));
        }
        return text;
      }
      return "";
    }

    @Override
    public String valueToString(@NotNull String value) {
      FileNameMatcherFactory.getInstance().createMatcher(value);
      return value;
    }

    @Override
    public boolean isValid(@NotNull String value) {
      if (value.isBlank()) return false;
      String withoutWildcards = value.trim().replaceAll("\\*", "a").replaceAll("\\?", "a");
      return PathUtil.isValidFileName(withoutWildcards, true);
    }
  }
}
