// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.ui;

import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorComboBoxRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.StringComboboxEditor;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;

public class NameSuggestionsField extends JPanel {
  private final @NotNull JComponent myComponent;
  private final EventListenerList myListenerList = new EventListenerList();
  private final @Nullable MyComboBoxModel myComboBoxModel;
  private final @NotNull Project myProject;
  private @Nullable MyDocumentListener myDocumentListener;
  private @Nullable MyComboBoxItemListener myComboBoxItemListener;

  private boolean myNonHumanChange = false;

  public NameSuggestionsField(@NotNull Project project) {
    super(new BorderLayout());
    myProject = project;
    myComboBoxModel = new MyComboBoxModel();
    final ComboBox<String> comboBox = new ComboBox<>(myComboBoxModel,-1);
    myComponent = comboBox;
    add(myComponent, BorderLayout.CENTER);
    setupComboBox(comboBox, StdFileTypes.JAVA);
  }

  /**
   * @deprecated specify the file type explicitly as the third constructor argument 
   */
  @Deprecated
  public NameSuggestionsField(String @Nullable [] nameSuggestions, @NotNull Project project) {
    this(nameSuggestions, project, StdFileTypes.JAVA);
  }
  
  public @NotNull Project getProject() {
    return myProject;
  }

  public NameSuggestionsField(String @Nullable [] nameSuggestions, @NotNull Project project, FileType fileType) {
    super(new BorderLayout());
    myProject = project;
    if (nameSuggestions == null || nameSuggestions.length <= 1 && !forceCombobox()) {
      myComponent = createTextFieldForName(nameSuggestions, fileType);
      myComboBoxModel = null;
    }
    else {
      myComboBoxModel = new MyComboBoxModel();
      myComboBoxModel.setSuggestions(nameSuggestions);
      final ComboBox<String> combobox = new ComboBox<>(myComboBoxModel);
      combobox.setSelectedIndex(0);
      setupComboBox(combobox, fileType);
      myComponent = combobox;
    }
    add(myComponent, BorderLayout.CENTER);
  }

  /**
   * @return whether combobox must be used even if there are no initial suggestions.
   * Subclasses may override this method and return true 
   * if they may add suggestions later via {@link #setSuggestions(String[])}.
   */
  protected boolean forceCombobox() {
    return false;
  }

  public NameSuggestionsField(final String[] suggestedNames, final @NotNull Project project, final FileType fileType, final @Nullable Editor editor) {
    this(suggestedNames, project, fileType);
    if (editor == null) return;
    // later here because EditorTextField creates Editor during addNotify()
    final Runnable selectionRunnable = () -> {
      ReadAction.run(() -> {
        final int offset = editor.getCaretModel().getOffset();
        List<TextRange> ranges = new ArrayList<>();
        SelectWordUtil.addWordSelection(editor.getSettings().isCamelWords(), editor.getDocument().getCharsSequence(), offset, ranges);
        Editor myEditor = getEditor();
        if (myEditor == null) return;
        for (TextRange wordRange : ranges) {
          String word = editor.getDocument().getText(wordRange);
          if (!word.equals(getEnteredName())) continue;
          final SelectionModel selectionModel = editor.getSelectionModel();
          myEditor.getSelectionModel().removeSelection();
          final int wordRangeStartOffset = wordRange.getStartOffset();
          int myOffset = offset - wordRangeStartOffset;
          myEditor.getCaretModel().moveToOffset(myOffset);
          TextRange selected = new TextRange(Math.max(0, selectionModel.getSelectionStart() - wordRangeStartOffset),
                                             Math.max(0, selectionModel.getSelectionEnd() - wordRangeStartOffset));
          selected = selected.intersection(new TextRange(0, myEditor.getDocument().getTextLength()));
          if (selectionModel.hasSelection() && selected != null && !selected.isEmpty()) {
            myEditor.getSelectionModel().setSelection(selected.getStartOffset(), selected.getEndOffset());
          }
          else if (shouldSelectAll()) {
            myEditor.getSelectionModel().setSelection(0, myEditor.getDocument().getTextLength());
          }
          break;
        }
      });
    };
    SwingUtilities.invokeLater(selectionRunnable);
  }

  protected boolean shouldSelectAll() {
    return true;
  }

  public void selectNameWithoutExtension() {
    SwingUtilities.invokeLater(() -> {
      EditorTextField textField = getEditorTextField();
      if (textField == null) return;
      final int pos = textField.getDocument().getText().lastIndexOf('.');
      if (pos > 0) {
        textField.select(TextRange.create(0, pos));
        textField.setCaretPosition(pos);
      }
    });
  }

  public void select(final int start, final int end) {
    SwingUtilities.invokeLater(() -> {
      EditorTextField textField = getEditorTextField();
      if (textField == null) return;
      textField.select(TextRange.create(start, end));
      textField.setCaretPosition(end);
    });
  }

  public void setSuggestions(final String @NotNull [] suggestions) {
    if(myComboBoxModel == null) return;
    @SuppressWarnings("unchecked") JComboBox<String> comboBox = (JComboBox<String>)myComponent;
    final String oldSelectedItem = (String)comboBox.getSelectedItem();
    final String oldItemFromTextField = (String) comboBox.getEditor().getItem();
    final boolean shouldUpdateTextField = oldItemFromTextField.equals(oldSelectedItem) || oldItemFromTextField.isBlank();
    myComboBoxModel.setSuggestions(suggestions);
    if(suggestions.length > 0 && shouldUpdateTextField) {
      if (oldSelectedItem != null) {
        comboBox.setSelectedItem(oldSelectedItem);
      } else {
        comboBox.setSelectedIndex(0);
      }
    }
    else {
      comboBox.getEditor().setItem(oldItemFromTextField);
    }
  }

  public @NotNull JComponent getComponent() {
    return this;
  }

  public JComponent getFocusableComponent() {
    if (myComponent instanceof JComboBox<?> comboBox) {
      return (JComponent)comboBox.getEditor().getEditorComponent();
    } else {
      return myComponent;
    }
  }

  public String getEnteredName() {
    if (myComponent instanceof JComboBox<?> comboBox) {
      return (String)comboBox.getEditor().getItem();
    } else {
      return ((EditorTextField) myComponent).getText();
    }
  }

  public boolean hasSuggestions() {
    return myComponent instanceof JComboBox;
  }

  private @NotNull JComponent createTextFieldForName(String @Nullable [] nameSuggestions, FileType fileType) {
    final String text;
    if (nameSuggestions != null && nameSuggestions.length > 0 && nameSuggestions[0] != null) {
      text = nameSuggestions[0];
    }
    else {
      text = "";
    }

    EditorTextField field = new EditorTextField(text, myProject, fileType);
    field.selectAll();
    return field;
  }

  private static final class MyComboBoxModel extends DefaultComboBoxModel<String> {
    private String[] mySuggestions;

    MyComboBoxModel() {
      mySuggestions = ArrayUtilRt.EMPTY_STRING_ARRAY;
    }

    // implements javax.swing.ListModel
    @Override
    public int getSize() {
      return mySuggestions.length;
    }

    // implements javax.swing.ListModel
    @Override
    public String getElementAt(int index) {
      return mySuggestions[index];
    }

    public void setSuggestions(String[] suggestions) {
      fireIntervalRemoved(this, 0, mySuggestions.length);
      mySuggestions = suggestions;
      fireIntervalAdded(this, 0, mySuggestions.length);
    }

  }

  private void setupComboBox(final @NotNull ComboBox<String> combobox, FileType fileType) {
    final EditorComboBoxEditor comboEditor = new StringComboboxEditor(myProject, fileType, combobox) {
      @Override
      public void setItem(Object anObject) {
        myNonHumanChange = true;
        super.setItem(anObject);
      }
    };

    combobox.setEditor(comboEditor);
    combobox.setRenderer(new EditorComboBoxRenderer(comboEditor));

    combobox.setEditable(true);
    combobox.setMaximumRowCount(8);

    comboEditor.selectAll();
  }

  private EditorTextField getEditorTextField() {
    if (myComponent instanceof EditorTextField field) {
      return field;
    }
    else {
      return ((EditorTextField)((JComboBox<?>)myComponent).getEditor().getEditorComponent());
    }
  }

  public @Nullable Editor getEditor() {
    return getEditorTextField().getEditor();
  }

  @FunctionalInterface
  public interface DataChanged extends EventListener {
    void dataChanged();
  }

  public void addDataChangedListener(DataChanged listener) {
    myListenerList.add(DataChanged.class, listener);
    attachListeners();
  }

  public void removeDataChangedListener(DataChanged listener) {
    myListenerList.remove(DataChanged.class, listener);
    if (myListenerList.getListenerCount() == 0) {
      detachListeners();
    }
  }

  private void attachListeners() {
    if (myDocumentListener == null) {
      myDocumentListener = new MyDocumentListener();
      ((EditorTextField) getFocusableComponent()).addDocumentListener(myDocumentListener);
    }
    if (myComboBoxItemListener == null && myComponent instanceof JComboBox<?> comboBox) {
      myComboBoxItemListener = new MyComboBoxItemListener();
      comboBox.addItemListener(myComboBoxItemListener);
    }
  }

  private void detachListeners() {
    if (myDocumentListener != null) {
      ((EditorTextField) getFocusableComponent()).removeDocumentListener(myDocumentListener);
      myDocumentListener = null;
    }
    if (myComboBoxItemListener != null) {
      ((JComboBox<?>) myComponent).removeItemListener(myComboBoxItemListener);
      myComboBoxItemListener = null;
    }
  }

  private void fireDataChanged() {
    Object[] list = myListenerList.getListenerList();

    for (Object aList : list) {
      if (aList instanceof DataChanged dataChanged) {
        dataChanged.dataChanged();
      }
    }
  }

  @Override
  public boolean requestFocusInWindow() {
    if(myComponent instanceof JComboBox<?> comboBox) {
      return comboBox.getEditor().getEditorComponent().requestFocusInWindow();
    }
    else {
      return myComponent.requestFocusInWindow();
    }
  }

  @Override
  public void setEnabled (boolean enabled) {
    myComponent.setEnabled(enabled);
  }

  private final class MyDocumentListener implements DocumentListener {
    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
      if (!myNonHumanChange && myComponent instanceof JComboBox<?> comboBox && comboBox.isPopupVisible()) {
        comboBox.hidePopup();
      }
      myNonHumanChange = false;

      fireDataChanged();
    }
  }

  private final class MyComboBoxItemListener implements ItemListener {
    @Override
    public void itemStateChanged(ItemEvent e) {
      fireDataChanged();
    }
  }

  @Override
  public void requestFocus() {
    getFocusableComponent().requestFocus();
  }
}
