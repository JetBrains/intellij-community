// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.fileTypes.FileTypesBundle;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory;

import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@ApiStatus.Internal
public final class IgnoredFilesAndFoldersPanel extends JPanel {
  private static final SimpleTextAttributes SEARCH_MATCH_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_SEARCH_MATCH, null);

  private final DefaultListModel<String> myModel = new DefaultListModel<>();
  private final JBList<String> myPatternList;
  private final Predicate<String> myValidator;
  private final @Nls String myInvalidMessage;
  private final boolean myLogInteractions;
  private final PatternEditField myEditField;
  // The pattern that the list paints with the highlight of the Settings search, see highlightPattern.
  private @Nullable @NlsSafe String myHighlightedPattern;

  IgnoredFilesAndFoldersPanel() {
    this(FileTypesBundle.message("filetype.ignore.text"), IgnoredFilesAndFoldersPanel::isValidFileNamePattern,
         FileTypesBundle.message("filetype.ignore.error.invalid"), true);
  }

  /**
   * @param hint the grey text below the list
   * @param validator accepts a pattern that the list can hold
   * @param invalidMessage the error for a pattern that {@code validator} rejects
   * @param logInteractions {@code true} to log the add, edit, and remove actions in {@link FileTypeConfigurableInteractions}
   */
  public IgnoredFilesAndFoldersPanel(@NotNull @Nls String hint,
                                     @NotNull Predicate<String> validator,
                                     @NotNull @Nls String invalidMessage,
                                     boolean logInteractions) {
    myValidator = validator;
    myInvalidMessage = invalidMessage;
    myLogInteractions = logInteractions;
    myEditField = new PatternEditField();
    setLayout(new BorderLayout());
    myPatternList = new JBList<>();
    myPatternList.setModel(myModel);
    myPatternList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myPatternList.setCellRenderer(new ColoredListCellRenderer<>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends String> list,
                                           @NlsSafe String value,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        append(value, value.equals(myHighlightedPattern) ? SEARCH_MATCH_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    });
    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myPatternList)
                                                 .setScrollPaneBorder(JBUI.Borders.empty())
                                                 .setPanelBorder(JBUI.Borders.customLine(JBColor.border(),1,1,0,1))
                                                 .setAddAction(_ -> addPattern())
                                                 .setEditAction(_ -> editPattern())
                                                 .setRemoveAction(_ -> removePattern())
                                                 .disableUpDownActions();
    add(decorator.createPanel(), BorderLayout.NORTH);
    JPanel listPanel = new JPanel(new BorderLayout());
    myEditField.setVisible(false);
    listPanel.add(myEditField, BorderLayout.NORTH);
    JBScrollPane scrollPane = new JBScrollPane(myPatternList);
    listPanel.add(scrollPane, BorderLayout.CENTER);
    scrollPane.setBorder(JBUI.Borders.customLine(JBColor.border(), 0, 1, 1, 1));
    add(listPanel, BorderLayout.CENTER);
    JLabel label = new JLabel(hint);
    label.setFont(JBUI.Fonts.smallFont());
    label.setForeground(JBColor.GRAY);
    label.setBorder(TitledSeparator.createEmptyBorder());
    add(label, BorderLayout.SOUTH);
  }

  /**
   * Selects {@code pattern} and paints it with the highlight of the Settings search, until the user edits or removes it. A pattern that the
   * list does not hold gets no selection.
   */
  public void highlightPattern(@NotNull @NlsSafe String pattern) {
    myHighlightedPattern = pattern;
    myPatternList.setSelectedValue(pattern, true);
    myPatternList.repaint();
  }

  private static boolean isValidFileNamePattern(@NotNull String value) {
    if (value.isBlank()) return false;
    String withoutWildcards = value.trim().replaceAll("\\*", "a").replaceAll("\\?", "a");
    return PathUtil.isValidFileName(withoutWildcards, true);
  }

  private void removePattern() {
    int index = myPatternList.getSelectedIndex();
    if (index >= 0) {
      forgetHighlight(myModel.get(index));
      if (myLogInteractions) FileTypeConfigurableInteractions.ignorePatternRemoved.log();
      myModel.remove(index);
      if (!myModel.isEmpty()) {
        if (index >= myModel.size()) index = myModel.size() - 1;
        myPatternList.setSelectedIndex(index);
      }
    }
  }

  private void editPattern() {
    if (myLogInteractions) FileTypeConfigurableInteractions.ignorePatternEdited.log();
    forgetHighlight(myPatternList.getSelectedValue());
    myEditField.startEdit(myPatternList.getSelectedValue());
  }

  private void addPattern() {
    if (myLogInteractions) FileTypeConfigurableInteractions.ignorePatternAdded.log();
    myEditField.startEdit("");
  }

  private void forgetHighlight(@Nullable String pattern) {
    if (pattern != null && pattern.equals(myHighlightedPattern)) myHighlightedPattern = null;
  }

  @NotNull String getValues() {
    return String.join(";", ArrayUtil.toStringArray(getPatterns()));
  }

  void setValues(@NotNull String values) {
    setPatterns(Arrays.asList(values.split(";")));
  }

  public @NotNull List<String> getPatterns() {
    return Collections.list(myModel.elements());
  }

  public void setPatterns(@NotNull List<String> patterns) {
    fillList(patterns);
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

  private final class PatternEditField extends JBTextField {
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
        setBorder(JBUI.Borders.customLine(JBColor.LIGHT_GRAY, 0, 1, 1, 1));
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
        showError(myInvalidMessage);
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
      int xLoc = getFontMetrics(getFont()).stringWidth(getText());
      RelativePoint relativePoint =
        new RelativePoint(this, new Point(xLoc, this.getHeight()));
      balloon.show(relativePoint, Balloon.Position.below);
    }
  }

  private final class PatternValueEditor extends TextFieldValueEditor<String> {

    PatternValueEditor(@NotNull JTextField field) {
      super(field, FileTypesBundle.message("filetype.ignore.pattern"), "");
    }

    @Override
    public @NotNull String parseValue(@Nullable String text) throws InvalidDataException {
      if (text != null) {
        if (!isValid(text)) {
          throw new InvalidDataException(myInvalidMessage);
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
      return myValidator.test(value);
    }
  }
}
