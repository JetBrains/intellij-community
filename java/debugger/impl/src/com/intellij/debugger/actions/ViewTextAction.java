/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeExpression;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.EditorTextField;

import javax.swing.*;
import java.awt.*;

/*
 * @author Jeka
 */
public class ViewTextAction extends BaseValueAction {
  protected void processText(final Project project, final String text, DebuggerTreeNodeImpl node, DebuggerContextImpl debuggerContext) {
    try {
      final TextWithImports expressionText = DebuggerTreeNodeExpression.createEvaluationText(node, debuggerContext);
      final MyDialog dialog = new MyDialog(project);
      final String title = "View Text: \"" + expressionText.getText() + "\"";
      dialog.setTitle(title);
      dialog.setText(text);
      dialog.show();
    }
    catch (EvaluateException e) {
      Messages.showErrorDialog(project, e.getMessage(), "View Text");
    }
  }


  private static class MyDialog extends DialogWrapper {

    private EditorTextField myTextViewer;

    private MyDialog(Project project) {
      super(project, false);
      setModal(false);
      setCancelButtonText("Close");
      setCrossClosesWindow(true);

      myTextViewer = new TextViewer(project);
      init();
    }

    public void setText(String text) {
      myTextViewer.setText(text);
    }

    protected Action[] createActions() {
      return new Action[] {getCancelAction()};
    }

    protected String getDimensionServiceKey() {
      return "#com.intellij.debugger.actions.ViewTextAction";
    }

    protected JComponent createCenterPanel() {
      final JPanel panel = new JPanel(new BorderLayout());
      panel.add(myTextViewer, BorderLayout.CENTER);
      panel.setPreferredSize(new Dimension(300, 200));
      return panel;
    }

  }


  private static class TextViewer extends EditorTextField {

    private TextViewer(Project project) {
      super(EditorFactory.getInstance().createDocument(""), project, FileTypes.PLAIN_TEXT, true, false);
    }

    protected EditorEx createEditor() {
      final EditorEx editor = super.createEditor();
      editor.setHorizontalScrollbarVisible(true);
      editor.setVerticalScrollbarVisible(true);
      editor.setEmbeddedIntoDialogWrapper(true);
      editor.getComponent().setPreferredSize(null);
      return editor;
    }
  }

}
