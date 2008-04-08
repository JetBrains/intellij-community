/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.ui.tree;

import com.intellij.lang.LanguagePerFileMappings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.fileTypes.FileOptionsProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public abstract class LanguagePerFileConfigurable<T> implements FileOptionsProvider {
  private final Project myProject;
  private final Class<T> myValueClass;
  private final LanguagePerFileMappings<T> myMappings;
  private final String myTreeTableTitle;
  private final String myOverrideQuestion;
  private final String myOverrideTitle;
  private AbstractFileTreeTable<T> myTreeView;
  private JScrollPane myTreePanel;
  private JPanel myPanel;
  private JLabel myLabel;

  protected LanguagePerFileConfigurable(final Project project, Class<T> valueClass, LanguagePerFileMappings<T> mappings, String caption, String treeTableTitle, String overrideQuestion, String overrideTitle) {
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
    myTreePanel = ScrollPaneFactory.createScrollPane(new JTable());
  }

  protected abstract String visualize(@NotNull T t);


  private class MyTreeTable extends AbstractFileTreeTable<T> {

    public MyTreeTable() {
      super(myProject, myValueClass, myTreeTableTitle);
      reset(myMappings.getMappings());
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
              .getSimpleContext(DataConstants.VIRTUAL_FILE, myVirtualFile, SimpleDataContext.getProjectContext(getProject()));
          AnActionEvent event = new AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, templatePresentation, ActionManager.getInstance(), 0);
          changeAction.update(event);
          editorComponent = comboComponent;
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              press(comboComponent);
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
            if (file != null && !isValueEditableForFile(file)) {
              append("N/A", SimpleTextAttributes.GRAYED_ATTRIBUTES);
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
      final List<T> values = myMappings.getAvailableValues();
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
