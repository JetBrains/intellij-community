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
package com.intellij.debugger.ui;

import com.intellij.debugger.engine.evaluation.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorComboBoxRenderer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author ven
 */
public class DebuggerExpressionComboBox extends DebuggerEditorImpl {
  public static final Key<String> KEY = Key.create("DebuggerComboBoxEditor.KEY");
  public static final int MAX_ROWS = 20;

  private MyEditorComboBoxEditor myEditor;
  private ComboBox myComboBox;

  private class MyEditorComboBoxEditor extends EditorComboBoxEditor {

    public MyEditorComboBoxEditor(Project project, FileType fileType) {
      super(project, fileType);
    }

    public Object getItem() {
      Document document = (Document)super.getItem();
      return createItem(document, getProject());
    }

    public void setItem(Object item) {
      final Object currentItem = getItem();
      if (currentItem == null || !currentItem.equals(item)) {
        super.setItem(createDocument((TextWithImports)item));
      }
      /* Causes PSI being modified from PSI events. See IDEADEV-22102
      final Editor editor = getEditor();
      if (editor != null) {
        DaemonCodeAnalyzer.getInstance(getProject()).updateVisibleHighlighters(editor);
      }
      */
    }

  }

  public DebuggerExpressionComboBox(Project project, @NonNls String recentsId) {
    this(project, null, recentsId, DefaultCodeFragmentFactory.getInstance());
  }

  public DebuggerExpressionComboBox(Project project, PsiElement context, @NonNls String recentsId, final CodeFragmentFactory factory) {
    super(project, context, recentsId, factory);
    setLayout(new BorderLayout(0, 0));

    myComboBox = new ComboBox(new MyComboboxModel(getRecents()), -1);

    // Have to turn this off because when used in DebuggerTreeInplaceEditor, the combobox popup is hidden on every change of selection
    // See comment to SynthComboBoxUI.FocusHandler.focusLost()
    myComboBox.setLightWeightPopupEnabled(false);

    myEditor = new MyEditorComboBoxEditor(getProject(), myFactory.getFileType());
    myComboBox.setRenderer(new EditorComboBoxRenderer(myEditor));

    myComboBox.setEditable(true);
    myComboBox.setEditor(myEditor);
    add(myComboBox);
  }

  public void selectPopupValue() {
    selectAll();
    final Object currentPopupValue = getCurrentPopupValue();
    if (currentPopupValue != null) {
      myComboBox.getModel().setSelectedItem(currentPopupValue);
      myComboBox.getEditor().setItem(currentPopupValue);
    }

    myComboBox.setPopupVisible(false);
  }

  public boolean isPopupVisible() {
    return myComboBox.isVisible() && myComboBox.isPopupVisible();
  }

  public void setPopupVisible(final boolean b) {
    myComboBox.setPopupVisible(b);
  }

  @Nullable
  public Object getCurrentPopupValue() {
    if (!isPopupVisible()) return null;

    final ComboPopup popup = myComboBox.getPopup();
    if (popup != null) {
      return popup.getList().getSelectedValue();
    }

    return null;
  }

  public void setText(TextWithImports item) {
    final String itemText = item.getText().replace('\n', ' ');
    item.setText(itemText);
    if (!"".equals(itemText)) {
      if (myComboBox.getItemCount() == 0 || !item.equals(myComboBox.getItemAt(0))) {
        myComboBox.insertItemAt(item, 0);
      }
    }
    if (myComboBox.getItemCount() > 0) {
      myComboBox.setSelectedIndex(0);
    }

    myComboBox.getEditor().setItem(item);
  }

  public TextWithImports getText() {
    return (TextWithImports)myComboBox.getEditor().getItem();
  }

  @Nullable
  private List<TextWithImports> getRecents() {
    final String recentsId = getRecentsId();
    if (recentsId != null) {
      final List<TextWithImports> result = new ArrayList<TextWithImports>();
      LinkedList<TextWithImports> recents = DebuggerRecents.getInstance(getProject()).getRecents(getRecentsId());
      for (final TextWithImports evaluationText : recents) {
        if (evaluationText.getText().indexOf('\n') == -1) {
          result.add(evaluationText);
        }
      }

      return result;
    }

    return null;
  }

  public Dimension getMinimumSize() {
    Dimension size = super.getMinimumSize();
    size.width = 100;
    return size;
  }

  public TextWithImports createText(String text, String importsString) {
    return new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, text, importsString);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myComboBox.setEnabled(enabled);
  }

  public JComponent getPreferredFocusedComponent() {
    return (JComponent)myComboBox.getEditor().getEditorComponent();
  }

  public void selectAll() {
    myComboBox.getEditor().selectAll();
  }

  public Editor getEditor() {
    return myEditor.getEditor();
  }

  public JComponent getEditorComponent() {
    return (JComponent)myEditor.getEditorComponent();
  }

  public void addRecent(TextWithImports text) {
    if (text.getText().length() != 0) {
      final Component editorComponent = myComboBox.getEditor().getEditorComponent();
      final boolean focusOwner = editorComponent.isFocusOwner();
      super.addRecent(text);
      myComboBox.insertItemAt(text, 0);
      myComboBox.setSelectedIndex(0);

      if (focusOwner) {
        editorComponent.requestFocus();
      }
    }
  }

  private static class MyComboboxModel extends AbstractListModel implements MutableComboBoxModel {
    private List<TextWithImports> myItems = new ArrayList<TextWithImports>();
    private int mySelectedIndex = -1;

    private MyComboboxModel(@Nullable final List<TextWithImports> recents) {
      if (recents != null) {
        myItems = recents;
      }
    }

    @Override
    public void setSelectedItem(final Object anItem) {
      final int oldSelectedIndex = mySelectedIndex;
      mySelectedIndex = anItem instanceof TextWithImports ? myItems.indexOf(anItem) : -1;
      if (oldSelectedIndex != mySelectedIndex) fireContentsChanged(this, -1, -1);
    }

    @Override
    public Object getSelectedItem() {
      return mySelectedIndex == -1 || mySelectedIndex > myItems.size() - 1 ? null : myItems.get(mySelectedIndex);
    }

    @Override
    public int getSize() {
      return myItems.size();
    }

    @Override
    public Object getElementAt(int index) {
      return myItems.get(index);
    }

    @Override
    public void addElement(final Object obj) {
      insertElementAt(obj, myItems.size() - 1);

      if (mySelectedIndex == -1 && myItems.size() == 1 && obj != null) {
        setSelectedItem(obj);
      }
    }

    @Override
    public void removeElement(Object obj) {
      removeElement(obj, true);
    }

    public void removeElement(final Object obj, final boolean checkSelection) {
      if (!(obj instanceof TextWithImports)) throw new IllegalArgumentException();
      final int index = myItems.indexOf((TextWithImports)obj);
      if (index != -1) {
        myItems.remove(index);

        if (checkSelection) {
          if (mySelectedIndex == index) {
            if (myItems.size() == 0) {
              setSelectedItem(null);
            }
            else if (index > myItems.size() - 1) {
              setSelectedItem(myItems.get(myItems.size() - 1));
            }
          }

          fireIntervalRemoved(this, index, index);
        }
      }
    }

    @Override
    public void insertElementAt(final Object obj, final int index) {
      if (!(obj instanceof TextWithImports)) throw new IllegalArgumentException();
      removeElement(obj, false); // remove duplicate entry if any

      myItems.add(index, (TextWithImports)obj);
      fireIntervalAdded(this, index, index);

      if (myItems.size() > MAX_ROWS) {
        for (int i = myItems.size() - 1; i > MAX_ROWS - 1; i--) {
          myItems.remove(i);
        }

        // will not fire events here to not recreate the editor
        //fireIntervalRemoved(this, myItems.size() - 1, MAX_ROWS - 1);
      }
    }

    @Override
    public void removeElementAt(final int index) {
      if (index < 0 || index > myItems.size() - 1) throw new IndexOutOfBoundsException();
      removeElement(myItems.get(index));
    }
  }
}