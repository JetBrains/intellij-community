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

/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 15, 2007
 * Time: 4:04:39 PM
 */
package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.tree.AbstractFileTreeTable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.charset.Charset;

public class FileTreeTable extends AbstractFileTreeTable<Charset> {
  public FileTreeTable(final Project project) {
    super(project, Charset.class, "Default Encoding");
    reset(EncodingProjectManager.getInstance(project).getAllMappings());

    getValueColumn().setCellRenderer(new DefaultTableCellRenderer(){
      public Component getTableCellRendererComponent(final JTable table, final Object value,
                                                     final boolean isSelected, final boolean hasFocus, final int row, final int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        final Charset t = (Charset)value;
        final Object userObject = table.getModel().getValueAt(row, 0);
        final VirtualFile file = userObject instanceof VirtualFile ? (VirtualFile)userObject : null;
        final Pair<String,Boolean> pair = ChangeEncodingUpdateGroup.update(file);
        final boolean enabled = file == null || pair.getSecond();
        if (t != null) {
          setText(t.displayName());
        }
        else if (file != null) {
          Charset charset = ChooseFileEncodingAction.charsetFromContent(file);
          if (charset != null) {
            setText(charset.displayName());
          }
          else if (LoadTextUtil.utfCharsetWasDetectedFromBytes(file)) {
            setText(file.getCharset().displayName());
          }
          else if (!ChooseFileEncodingAction.isEnabled(file)) {
            setText("N/A");
          }
        }
        setEnabled(enabled);
        return this;
      }
    });

    getValueColumn().setCellEditor(new DefaultCellEditor(new JComboBox()){
      private VirtualFile myVirtualFile;
      {
        delegate = new EditorDelegate() {
            public void setValue(Object value) {
              getTableModel().setValueAt(value, new DefaultMutableTreeNode(myVirtualFile), -1);
            }

	    public Object getCellEditorValue() {
		return getTableModel().getValueAt(new DefaultMutableTreeNode(myVirtualFile), 1);
	    }
        };
      }

      public Component getTableCellEditorComponent(JTable table, final Object value, boolean isSelected, int row, int column) {
        final Object o = table.getModel().getValueAt(row, 0);
        myVirtualFile = o instanceof Project ? null : (VirtualFile)o;

        final ChooseFileEncodingAction changeAction = new ChooseFileEncodingAction(myVirtualFile){
          protected void chosen(VirtualFile virtualFile, Charset charset) {
            getValueColumn().getCellEditor().stopCellEditing();
            if (clearSubdirectoriesOnDemandOrCancel(
                virtualFile, "There are encodings specified for the subdirectories. Override them?", "Override Subdirectory Encoding")) {
              getTableModel().setValueAt(charset, new DefaultMutableTreeNode(virtualFile), 1);
            }
          }
        };
        Presentation templatePresentation = changeAction.getTemplatePresentation();
        final JComponent comboComponent = changeAction.createCustomComponent(templatePresentation);

        DataContext dataContext = SimpleDataContext
          .getSimpleContext(PlatformDataKeys.VIRTUAL_FILE.getName(), myVirtualFile, SimpleDataContext.getProjectContext(getProject()));
        AnActionEvent event =
          new AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, templatePresentation, ActionManager.getInstance(), 0);
        changeAction.update(event);
        editorComponent = comboComponent;
        comboComponent.addComponentListener(new ComponentAdapter() {
          @Override
          public void componentShown(final ComponentEvent e) {
            press((Container)e.getComponent());
          }
        });
        Charset charset = (Charset)getTableModel().getValueAt(new DefaultMutableTreeNode(myVirtualFile), 1);
        templatePresentation.setText(charset == null ? "" : charset.displayName());
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
    return ChangeEncodingUpdateGroup.update(virtualFile).getSecond();
  }
}
