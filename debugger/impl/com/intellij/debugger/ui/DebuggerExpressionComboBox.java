package com.intellij.debugger.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
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

    public Document getDocument() {
      return (Document)super.getItem();
    }

    public Object getItem() {
      Document document = (Document)super.getItem();
      return createItem(document, getProject());
    }

    public void setItem(Object item) {
      super.setItem(createDocument((TextWithImports)item));
      final Editor editor = getEditor();
      if (editor != null) {
        DaemonCodeAnalyzer.getInstance(getProject()).updateVisibleHighlighters(editor);
      }
    }

  }

  public void setText(TextWithImports item) {
    item.setText(item.getText().replace('\n', ' '));
    if(myComboBox.getItemCount() == 0 || !item.equals(myComboBox.getItemAt(0))) {
      myComboBox.insertItemAt(item, 0);
    }
    myComboBox.setSelectedIndex(0);
    if(myComboBox.isEditable()) {
      myComboBox.getEditor().setItem(item);
    }
    else {
      myItem =  item;
    }
  }

  public TextWithImports getText() {
    return (TextWithImports)myComboBox.getEditor().getItem();
  }

  protected void updateEditor(TextWithImports item) {
    if(!myComboBox.isEditable()) return;

    boolean focusOwner = myEditor != null ? myEditor.getEditorComponent().isFocusOwner() : false;
    int offset = 0;
    TextWithImports oldItem = null;
    if(focusOwner) {
      offset = myEditor.getEditor().getCaretModel().getOffset();
      oldItem = (TextWithImports)myEditor.getItem();
    }
    myEditor = new MyEditorComboBoxEditor(getProject(), StdFileTypes.JAVA);
    myComboBox.setEditor(myEditor);
    myComboBox.setRenderer(new EditorComboBoxRenderer(myEditor));

    myComboBox.setMaximumRowCount(MAX_ROWS);

    myEditor.setItem(item);
    if(focusOwner) {
      myEditor.getEditorComponent().requestFocus();
      if(oldItem.equals(item)) {
        myEditor.getEditor().getCaretModel().moveToOffset(offset);
      }
    }
  }

  private void setRecents() {
    boolean focusOwner = myEditor != null ? myEditor.getEditorComponent().isFocusOwner() : false;
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
      if(myComboBox.isEditable() == enabled) return;

      myComboBox.setEnabled(enabled);
      myComboBox.setEditable(enabled);

      if(enabled) {
        setRecents();
        updateEditor(myItem);
      } else {
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
    this(project, null, recentsId);
  }

  public DebuggerExpressionComboBox(Project project, PsiElement context, @NonNls String recentsId) {
    super(project, context, recentsId);
    myComboBox = new ComboBox(-1);
    // Have to turn this off because when used in InplaceEditor, the combobox pupup is hidden on every change of selection
    // See comment to SynthComboBoxUI.FocusHandler.focusLost()
    myComboBox.setLightWeightPopupEnabled(false);
    setLayout(new BorderLayout());
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
