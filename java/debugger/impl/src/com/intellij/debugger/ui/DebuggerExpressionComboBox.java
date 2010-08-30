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

import javax.swing.*;
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
  protected TextWithImports myItem;

  private class MyEditorComboBoxEditor extends EditorComboBoxEditor{
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

  public void setText(TextWithImports item) {
    final String itemText = item.getText().replace('\n', ' ');
    item.setText(itemText);
    if (!"".equals(itemText)) {
      if(myComboBox.getItemCount() == 0 || !item.equals(myComboBox.getItemAt(0))) {
        myComboBox.insertItemAt(item, 0);
      }
    }
    if (myComboBox.getItemCount() > 0) {
      myComboBox.setSelectedIndex(0);
    }

    myComboBox.getEditor().setItem(item);
    myItem = item;
  }

  public TextWithImports getText() {
    return (TextWithImports)myComboBox.getEditor().getItem();
  }

  private void setRecents() {
    boolean focusOwner = myEditor != null && myEditor.getEditorComponent().isFocusOwner();
    myComboBox.removeAllItems();
    if(getRecentsId() != null) {
      LinkedList<TextWithImports> recents = DebuggerRecents.getInstance(getProject()).getRecents(getRecentsId());
      ArrayList<TextWithImports> singleLine = new ArrayList<TextWithImports>();
      for (TextWithImports evaluationText : recents) {
        if (evaluationText.getText().indexOf('\n') == -1) {
          singleLine.add(evaluationText);
        }
      }
      addRecents(singleLine);
    }
    if(focusOwner) myEditor.getEditorComponent().requestFocus();
  }

  public Dimension getMinimumSize() {
    Dimension size = super.getMinimumSize();
    size.width = 100;
    return size;
  }

  public void setEnabled(boolean enabled) {
    myComboBox.setEnabled(enabled);

    if (enabled) {
      setRecents();
      myEditor.setItem(myItem);
    }
    else {
      myItem = (TextWithImports)myComboBox.getEditor().getItem();
      myComboBox.removeAllItems();
      myComboBox.addItem(myItem);
    }
  }

  public TextWithImports createText(String text, String importsString) {
    return new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, text, importsString);
  }

  private void addRecents(List<TextWithImports> expressions) {
    for (final TextWithImports text : expressions) {
      myComboBox.addItem(text);
    }
    if (myComboBox.getItemCount() > 0) {
      myComboBox.setSelectedIndex(0);
    }
  }

  public DebuggerExpressionComboBox(Project project, @NonNls String recentsId) {
    this(project, null, recentsId, DefaultCodeFragmentFactory.getInstance());
  }

  public DebuggerExpressionComboBox(Project project, PsiElement context, @NonNls String recentsId, final CodeFragmentFactory factory) {
    super(project, context, recentsId, factory);
    myComboBox = new ComboBox(-1);
    myComboBox.setEditable(true);

    myEditor = new MyEditorComboBoxEditor(getProject(), myFactory.getFileType());
    myComboBox.setEditor(myEditor);
    myComboBox.setRenderer(new EditorComboBoxRenderer(myEditor));

    // Have to turn this off because when used in DebuggerTreeInplaceEditor, the combobox popup is hidden on every change of selection
    // See comment to SynthComboBoxUI.FocusHandler.focusLost()
    myComboBox.setLightWeightPopupEnabled(false);
    setLayout(new BorderLayout(0, 0));
    add(myComboBox);

    setText(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, ""));
    myItem =  createText("");
    setEnabled(true);
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
    super.addRecent(text);
    setRecents();
  }
}
