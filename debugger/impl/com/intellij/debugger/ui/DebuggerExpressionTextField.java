package com.intellij.debugger.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

public class DebuggerExpressionTextField extends DebuggerEditorImpl {
  private final EditorTextField myEditor;
  private final JTextField myStubField = new JTextField();
  private final JPanel myMainPanel = new JPanel(new CardLayout());
  private static final @NonNls String EDITOR = "editor";
  private static final @NonNls String STUB = "stub";

  public DebuggerExpressionTextField(Project project, PsiElement context, final @NonNls String recentsId) {
    super(project, context, recentsId);
    myStubField.setEnabled(false);
    myEditor = new EditorTextField("", project, StdFileTypes.JAVA);
    setLayout(new BorderLayout());
    myMainPanel.add(myStubField, STUB);
    myMainPanel.add(myEditor, EDITOR);
    add(myMainPanel, BorderLayout.CENTER);
    ((CardLayout)myMainPanel.getLayout()).show(myMainPanel, isEnabled()? EDITOR : STUB);
    setText(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, ""));
  }

  public JComponent getPreferredFocusedComponent() {
    return myEditor.getEditor().getContentComponent();
  }

  public void selectAll() {
    myEditor.selectAll();
  }

  public TextWithImports getText() {
    return createItem(myEditor.getDocument(), getProject());
  }

  public void setText(TextWithImports text) {
    myEditor.setDocument(createDocument(text));
    final Editor editor = myEditor.getEditor();
    if (editor != null) {
      DaemonCodeAnalyzer.getInstance(getProject()).updateVisibleHighlighters(editor);
    }
  }

  public TextWithImports createText(String text, String importsString) {
    return new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, text, importsString);
  }

  public void setEnabled(boolean enabled) {
    if (isEnabled() != enabled) {
      super.setEnabled(enabled);
      final TextWithImports text = getText();
      myStubField.setText(text.getText());
      ((CardLayout)myMainPanel.getLayout()).show(myMainPanel, enabled? EDITOR : STUB);
    }
  }
}
