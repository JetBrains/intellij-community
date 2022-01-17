/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.util.xml.ui;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JavaReferenceEditorUtil;
import com.intellij.util.ui.AbstractTableCellEditor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.EventObject;

/**
 * @author peter
 */
public class PsiClassTableCellEditor extends AbstractTableCellEditor {
  private final Project myProject;
  private final GlobalSearchScope mySearchScope;
  private EditorTextField myEditor;

  public PsiClassTableCellEditor(final Project project, final GlobalSearchScope searchScope) {
    myProject = project;
    mySearchScope = searchScope;
  }

  @Override
  public final Object getCellEditorValue() {
    return myEditor.getText();
  }

  @Override
  public final boolean stopCellEditing() {
    final boolean b = super.stopCellEditing();
    myEditor = null;
    return b;
  }

  @Override
  public boolean isCellEditable(EventObject e) {
    return !(e instanceof MouseEvent) || ((MouseEvent)e).getClickCount() >= 2;
  }

  @Override
  public final Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    final Document document = JavaReferenceEditorUtil.createDocument(value == null ? "" : (String)value, myProject, true);
    myEditor = new EditorTextField(document, myProject, JavaFileType.INSTANCE){
      @Override
      protected boolean shouldHaveBorder() {
        return false;
      }

      @Override
      protected void onEditorAdded(@NotNull Editor editor) {
        super.onEditorAdded(editor);
        final JComponent editorComponent = editor.getContentComponent();
        editorComponent.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "ENTER");
        editorComponent.getActionMap().put("ENTER", new AbstractAction() {
          @Override
          public void actionPerformed(ActionEvent e) {
            stopCellEditing();
          }
        });
      }
    };
    final JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.add(myEditor);
    final FixedSizeButton button = new FixedSizeButton(myEditor);
    panel.add(button, BorderLayout.EAST);
    button.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject)
          .createInheritanceClassChooser(JavaBundle.message("choose.class"), mySearchScope, null, true, true, Conditions.alwaysTrue());
        chooser.showDialog();
        final PsiClass psiClass = chooser.getSelected();
        if (psiClass != null) {
          myEditor.setText(psiClass.getQualifiedName());
        }
      }
    });
    panel.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        if (!e.isTemporary() && myEditor != null) {
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myEditor, true));
        }
      }

      @Override
      public void focusLost(FocusEvent e) {
      }
    });
    myEditor.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
      }

      @Override
      public void focusLost(FocusEvent e) {
        if (!e.isTemporary()) {
          stopCellEditing();
        }
      }
    });

    //ComponentWithBrowseButton.MyDoClickAction.addTo(button, myEditor);

    return panel;
  }
}
