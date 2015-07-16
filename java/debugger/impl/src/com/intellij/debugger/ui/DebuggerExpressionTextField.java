/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.DefaultCodeFragmentFactory;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class DebuggerExpressionTextField extends DebuggerEditorImpl {
  private final EditorTextField myEditor;
  private final JTextField myStubField = new JTextField();
  private final JPanel myMainPanel = new JPanel(new CardLayout());
  private static final @NonNls String EDITOR = "editor";
  private static final @NonNls String STUB = "stub";

  public DebuggerExpressionTextField(@NotNull Project project, @NotNull Disposable parentDisposable, @Nullable PsiElement context, @Nullable String recentsId) {
    super(project, DefaultCodeFragmentFactory.getInstance(), parentDisposable, context, recentsId);

    myStubField.setEnabled(false);
    myEditor = new EditorTextField("", project, StdFileTypes.JAVA);
    setLayout(new BorderLayout());
    myMainPanel.add(myStubField, STUB);
    myMainPanel.add(addChooseFactoryLabel(myEditor, false), EDITOR);
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

  @Override
  protected void doSetText(TextWithImports text) {
    restoreFactory(text);
    myEditor.setNewDocumentAndFileType(getCurrentFactory().getFileType(), createDocument(text));
  }

  @Override
  protected void updateEditorUi() {
    final Editor editor = myEditor.getEditor();
    if (editor != null) {
      DaemonCodeAnalyzer.getInstance(getProject()).updateVisibleHighlighters(editor);
    }
  }

  public TextWithImports createText(String text, String importsString) {
    return new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, text, importsString, getCurrentFactory().getFileType());
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
