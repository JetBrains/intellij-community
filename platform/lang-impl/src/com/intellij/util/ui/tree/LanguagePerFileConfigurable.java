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
package com.intellij.util.ui.tree;

import com.intellij.lang.PerFileMappings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.*;
import java.util.List;

/**
 * @author peter
 */
public abstract class LanguagePerFileConfigurable<T> implements SearchableConfigurable {
  protected final Project myProject;
  private final Class<T> myValueClass;
  private final PerFileMappings<T> myMappings;
  private final String myTreeTableTitle;
  private final String myOverrideQuestion;
  private final String myOverrideTitle;
  private AbstractFileTreeTable<T> myTreeView;
  private JScrollPane myTreePanel;
  private JPanel myPanel;
  private JLabel myLabel;

  protected LanguagePerFileConfigurable(final Project project, Class<T> valueClass, PerFileMappings<T> mappings, String caption, String treeTableTitle, String overrideQuestion, String overrideTitle) {
    myProject = project;
    myValueClass = valueClass;
    myMappings = mappings;
    myTreeTableTitle = treeTableTitle;
    myOverrideQuestion = overrideQuestion;
    myOverrideTitle = overrideTitle;
    myLabel.setText(caption);
  }

  public JComponent createComponent() {
    myTreeView = new MyTreeTable();
    myTreePanel.setViewportView(myTreeView);
    return myPanel;
  }

  public boolean isModified() {
    Map<VirtualFile, T> mapping = myMappings.getMappings();
    boolean same = myTreeView.getValues().equals(mapping);
    return !same;
  }

  public void apply() throws ConfigurationException {
    myMappings.setMappings(myTreeView.getValues());
  }

  public void reset() {
    myTreeView.reset(myMappings.getMappings());
 }

  public void disposeUIResources() {
  }

  public void selectFile(@NotNull VirtualFile virtualFile) {
    myTreeView.select(virtualFile);
  }

  private void createUIComponents() {
    myTreePanel = ScrollPaneFactory.createScrollPane(new JBTable());
  }

  protected abstract String visualize(@NotNull T t);

  public AbstractFileTreeTable<T> getTreeView() {
    return myTreeView;
  }

  public String getId() {
    return getDisplayName();
  }

  public Runnable enableSearch(final String option) {
    return null;
  }

  private class MyTreeTable extends AbstractFileTreeTable<T> {

    public MyTreeTable() {
      super(myProject, myValueClass, myTreeTableTitle);
      getValueColumn().setCellEditor(new DefaultCellEditor(new JComboBox()) {
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

          final ChooseSomethingAction changeAction = new ChooseSomethingAction(myVirtualFile) {
            public void update(final AnActionEvent e) {
              boolean enabled = isValueEditableForFile(myVirtualFile);
              if (myVirtualFile != null) {
                final T mapping = myMappings.getMapping(myVirtualFile);
                e.getPresentation().setText(mapping == null ? "" : visualize(mapping));
              }
              e.getPresentation().setEnabled(enabled);
            }

            protected void chosen(final VirtualFile virtualFile, final T charset) {
              getValueColumn().getCellEditor().stopCellEditing();
              if (clearSubdirectoriesOnDemandOrCancel(virtualFile, myOverrideQuestion, myOverrideTitle)) {
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
          final T t = (T)getTableModel().getValueAt(new DefaultMutableTreeNode(myVirtualFile), 1);
          templatePresentation.setText(t == null ? "" : visualize(t));
          comboComponent.revalidate();

          return editorComponent;
        }
      });
      getValueColumn().setCellRenderer(new ColoredTableCellRenderer() {
        protected void customizeCellRenderer(final JTable table,
                                             final Object value,
                                             final boolean selected,
                                             final boolean hasFocus,
                                             final int row,
                                             final int column) {
          final T t = (T)value;
          if (t != null) {
            append(visualize(t), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
          else {
            final Object userObject = table.getModel().getValueAt(row, 0);
            final VirtualFile file = userObject instanceof VirtualFile ? (VirtualFile)userObject : null;
            if (file != null) {
              if (handleDefaultValue(file, this)) {
                return;
              }

              if (!isValueEditableForFile(file)) {
                append("N/A", SimpleTextAttributes.GRAYED_ATTRIBUTES);
              }
            }
          }
        }
      });
    }

    @Override
    protected boolean isValueEditableForFile(final VirtualFile virtualFile) {
      boolean enabled = true;
      if (virtualFile != null) {
        if (!virtualFile.isDirectory()) {
          final FileType fileType = virtualFile.getFileType();
          if (fileType.isBinary()) {
            enabled = false;
          }
        }
      }
      return enabled;
    }

  }

  protected boolean handleDefaultValue(VirtualFile file, ColoredTableCellRenderer renderer) {
    return false;
  }

  private abstract class ChooseSomethingAction extends ComboBoxAction {
    private final VirtualFile myVirtualFile;

    public ChooseSomethingAction(final VirtualFile virtualFile) {
      myVirtualFile = virtualFile;
    }

    @NotNull
    protected DefaultActionGroup createPopupActionGroup(final JComponent button) {
      return createGroup(true);
    }

    private ChangeSomethingAction createChooseAction(final VirtualFile virtualFile, final T t) {
      return new ChangeSomethingAction(virtualFile, t){
        protected void chosen(final VirtualFile file, final T t) {
          ChooseSomethingAction.this.chosen(file, t);
        }
      };
    }

    protected abstract void chosen(final VirtualFile virtualFile, final T charset);

    public DefaultActionGroup createGroup(final boolean showClear) {
      DefaultActionGroup group = new DefaultActionGroup();
      if (showClear) {
        group.add(createChooseAction(myVirtualFile, null));
      }
      final List<T> values = new ArrayList<T>(myMappings.getAvailableValues(myVirtualFile));
      Collections.sort(values, new Comparator<T>() {
        public int compare(final T o1, final T o2) {
          return visualize(o1).compareTo(visualize(o2));
        }
      });
      for (T t : values) {
        group.add(createChooseAction(myVirtualFile, t));
      }
      return group;
    }

    private abstract class ChangeSomethingAction extends AnAction {
      private final VirtualFile myFile;
      private final T myDialect;

      ChangeSomethingAction(@Nullable final VirtualFile file, @Nullable final T t) {
        super("", "", null);
        getTemplatePresentation().setText(t == null ? "Clear" : visualize(t));
        myFile = file;
        myDialect = t;
      }

      public void actionPerformed(final AnActionEvent e) {
        chosen(myFile, myDialect);
      }

      protected abstract void chosen(final VirtualFile file, final T t);
    }
  }
}
