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

/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 15, 2007
 * Time: 4:04:39 PM
 */
package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.util.ui.tree.AbstractFileTreeTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

class EncodingFileTreeTable extends AbstractFileTreeTable<Charset> {
  EncodingFileTreeTable(@NotNull Project project) {
    super(project, Charset.class, "Default Encoding", VirtualFileFilter.ALL, false);
    Map<VirtualFile, Charset> mappings = FileEncodingConfigurable.getExistingMappingIncludingDefault(project);
    reset(mappings);
    getValueColumn().setCellRenderer(new DefaultTableCellRenderer(){
      @Override
      public Component getTableCellRendererComponent(final JTable table, final Object value,
                                                     final boolean isSelected, final boolean hasFocus, final int row, final int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        final Charset t = (Charset)value;
        final Object userObject = table.getModel().getValueAt(row, 0);
        final VirtualFile file = userObject instanceof VirtualFile ? (VirtualFile)userObject : null;
        Pair<Charset, String> check = file == null || file.isDirectory() ? null : EncodingUtil.checkSomeActionEnabled(file);
        String failReason = check == null ? null : check.second;
        boolean enabled = failReason == null;

        // show existing encoding only if it was specified explicitly or it is unchangeable (with reason)
        boolean toShow = t != null || failReason != null;

        if (toShow) {
          Charset existing = check == null ? null : check.first;
          String encodingText = t != null ? t.displayName() : existing == null ? "N/A" : existing.displayName();
          setText(encodingText + (failReason == null ? "" : " (" + failReason + ")"));
        }

        setEnabled(enabled);
        return this;
      }
    });

    getValueColumn().setCellEditor(new DefaultCellEditor(new JComboBox()){
      private VirtualFile myVirtualFile;
      {
        delegate = new EditorDelegate() {
            @Override
            public void setValue(Object value) {
              getTableModel().setValueAt(value, new DefaultMutableTreeNode(myVirtualFile), -1);
            }

	    @Override
            public Object getCellEditorValue() {
		return getTableModel().getValueAt(new DefaultMutableTreeNode(myVirtualFile), 1);
	    }
        };
      }

      @Override
      public Component getTableCellEditorComponent(JTable table, final Object value, boolean isSelected, int row, int column) {
        myVirtualFile = (VirtualFile)table.getModel().getValueAt(row, 0);
        byte[] b = null;
        try {
          b = myVirtualFile == null || myVirtualFile.isDirectory() ? null : myVirtualFile.contentsToByteArray();
        }
        catch (IOException ignored) {
        }
        final byte[] bytes = b;
        final Document document = myVirtualFile == null ? null : FileDocumentManager.getInstance().getDocument(myVirtualFile);

        final ChangeFileEncodingAction cfa = new ChangeFileEncodingAction(true) {
          @Override
          protected boolean chosen(Document document,
                                   Editor editor,
                                   @NotNull VirtualFile virtualFile,
                                   byte[] bytes,
                                   @NotNull Charset charset) {
            getValueColumn().getCellEditor().stopCellEditing();
            getTableModel().setValueAt(charset, new DefaultMutableTreeNode(virtualFile), 1);
            return true;
          }
        };
        ComboBoxAction changeAction = new ComboBoxAction() {
          @NotNull
          @Override
          protected DefaultActionGroup createPopupActionGroup(JComponent button) {
            return cfa.createActionGroup(myVirtualFile, null, document, bytes, "<Clear>");
          }
        };
        DataContext dataContext = SimpleDataContext.getSimpleContext(CommonDataKeys.VIRTUAL_FILE.getName(), myVirtualFile,
                                                                      SimpleDataContext.getProjectContext(getProject()));
        AnActionEvent event = AnActionEvent.createFromAnAction(changeAction, null, ActionPlaces.UNKNOWN, dataContext);
        Presentation presentation = event.getPresentation();
        JComponent comboComponent = changeAction.createCustomComponent(presentation);

        changeAction.update(event);
        presentation.setDescription(null);
        if (myVirtualFile == null) {
          presentation.setEnabled(true); // enable changing encoding for tree root (entire project)
        }
        editorComponent = comboComponent;
        comboComponent.addComponentListener(new ComponentAdapter() {
          @Override
          public void componentShown(final ComponentEvent e) {
            press((Container)e.getComponent());
          }
        });
        Charset charset = (Charset)getTableModel().getValueAt(new DefaultMutableTreeNode(myVirtualFile), 1);
        presentation.setText(charset == null ? "" : charset.displayName());
        comboComponent.setToolTipText(null);
        comboComponent.revalidate();

        return editorComponent;
      }
    });
  }

  @Override
  protected boolean isNullObject(final Charset value) {
    return value == ChooseFileEncodingAction.NO_ENCODING;
  }

  @Override
  protected boolean isValueEditableForFile(final VirtualFile virtualFile) {
    return virtualFile == null || virtualFile.isDirectory() ||
           EncodingUtil.checkSomeActionEnabled(virtualFile) == null;
  }
}
