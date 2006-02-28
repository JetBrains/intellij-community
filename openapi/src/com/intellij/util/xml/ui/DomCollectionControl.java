/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.j2ee.j2eeDom.xmlData.ReadOnlyDeploymentDescriptorModificationException;
import com.intellij.javaee.ui.Warning;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.xml.ClassChooser;
import com.intellij.util.xml.ClassChooserManager;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.ui.DomEditorManager;
import com.intellij.util.xml.ui.DomUIControl;
import com.intellij.util.xml.ui.CommitListener;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class DomCollectionControl<T extends DomElement> implements DomUIControl {
  private final EventDispatcher<CommitListener> myDispatcher = EventDispatcher.create(CommitListener.class);
  protected DomCollectionPanel myCollectionPanel;

  private final List<T> myData = new ArrayList<T>();
  private final DomElement myParentDomElement;
  private final DomCollectionChildDescription myChildDescription;
  private ColumnInfo<T, ?>[] myColumnInfos;
  private boolean myEditable = false;

  public DomCollectionControl(DomElement parentElement,
                              DomCollectionChildDescription description,
                              final boolean editable,
                              ColumnInfo<T, ?>... columnInfos) {
    myChildDescription = description;
    myParentDomElement = parentElement;
    myColumnInfos = columnInfos;
    myEditable = editable;
  }

  public DomCollectionControl(DomElement parentElement,
                              @NonNls String subTagName,
                              final boolean editable,
                              ColumnInfo<T, ?>... columnInfos) {
    this(parentElement, parentElement.getGenericInfo().getCollectionChildDescription(subTagName), editable, columnInfos);
  }

  public DomCollectionControl(DomElement parentElement, DomCollectionChildDescription description) {
    myChildDescription = description;
    myParentDomElement = parentElement;
  }

  public DomCollectionControl(DomElement parentElement, @NonNls String subTagName) {
    this(parentElement, parentElement.getGenericInfo().getCollectionChildDescription(subTagName));
  }

  public boolean isEditable() {
    return myEditable;
  }

  public final ColumnInfo<T, ?>[] getColumnInfos() {
    return myColumnInfos;
  }

  public void bind(JComponent component) {
    assert component instanceof DomCollectionPanel;

    initialize((DomCollectionPanel)component);
  }

  public void addCommitListener(CommitListener listener) {
      myDispatcher.addListener(listener);
    }

    public void removeCommitListener(CommitListener listener) {
      myDispatcher.removeListener(listener);
    }

  protected void initialize(final DomCollectionPanel boundComponent) {
    if (boundComponent == null) {
      myCollectionPanel = new DomCollectionPanel();
    }
    else {
      myCollectionPanel = boundComponent;
    }
    myCollectionPanel.setControl(this);
    if (myColumnInfos == null) {
      myColumnInfos = createColumnInfos(myParentDomElement);
    }

    reset();
    initializeTable();
  }

  protected ColumnInfo[] createColumnInfos(DomElement parent) {
    throw new UnsupportedOperationException("Should initialize column infos");
  }

  protected void initializeTable() {
    JTable table = myCollectionPanel.getTable();

    myCollectionPanel.setTableModel(new AbstractTableModel() {
      public int getRowCount() {
        return myData.size();
      }

      public int getColumnCount() {
        return myColumnInfos.length;
      }

      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return myColumnInfos[columnIndex].isCellEditable(myData.get(rowIndex));
      }

      public String getColumnName(int column) {
        return myColumnInfos[column].getName();
      }

      public Class getColumnClass(int columnIndex) {
        return myColumnInfos[columnIndex].getColumnClass();
      }

      public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
        final Object oldValue = getValueAt(rowIndex, columnIndex);
        if (!Comparing.equal(oldValue, aValue)) {
          performWriteCommandAction(new WriteCommandAction(getProject()) {
            protected void run(final Result result) throws Throwable {
              ((ColumnInfo<T, Object>)myColumnInfos[columnIndex]).setValue(myData.get(rowIndex), aValue);
            }
          });
        }
      }

      public Object getValueAt(int rowIndex, int columnIndex) {
        return myColumnInfos[columnIndex].valueOf(myData.get(rowIndex));
      }
    });

    for (int i = 0; i < myColumnInfos.length; i++) {
      ColumnInfo<T, ?> columnInfo = myColumnInfos[i];
      final TableColumn column = table.getColumnModel().getColumn(i);
      final TableCellRenderer cellRenderer = columnInfo.getRenderer(null);
      if (cellRenderer != null) {
        column.setCellRenderer(cellRenderer);

        int width = -1;
        for (int j = 0; j < myData.size(); j++) {
          T t = myData.get(j);
          final Component component = cellRenderer.getTableCellRendererComponent(table, columnInfo.valueOf(t), false, false, j, i);
          final int prefWidth = component.getPreferredSize().width;
          if (prefWidth > width) {
            width = prefWidth;
          }
        }
        if (width > 0) {
          column.setPreferredWidth(width);
        }
      }
      final TableCellEditor cellEditor = columnInfo.getEditor(null);
      if (cellEditor != null) {
        column.setCellEditor(cellEditor);
      }
    }
    reset();
  }

  protected void doEdit() {
    doEdit(myData.get(myCollectionPanel.getTable().getSelectedRow()));
  }

  protected void doEdit(final T t) {
    final DomEditorManager manager = getDomEditorManager(this);
    if (manager != null) {
      manager.openDomElementEditor(t);
    }
  }

  protected void removeInternal(final Consumer<T> consumer, final List<T> toDelete) {
    new WriteCommandAction(getProject()) {
      protected void run(Result result) throws Throwable {
        for (final T t : toDelete) {
          consumer.consume(t);
        }
      }
    }.execute();
  }

  protected void doRemove() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final int[] selected = myCollectionPanel.getTable().getSelectedRows();
        if (selected == null || selected.length == 0) return;
        List<T> selectedElements = new ArrayList<T>(selected.length);
        for (final int i : selected) {
          selectedElements.add(myData.get(i));
        }
        removeInternal(new Consumer<T>() {
          public void consume(final T t) {
            if (t.isValid()) {
              t.undefine();
            }
            myData.remove(t);
          }
        }, selectedElements);

        myCollectionPanel.getTableModel().fireTableDataChanged();
        int selection = selected[0];
        if (selection >= myData.size()) {
          selection = myData.size() - 1;
        }
        if (selection >= 0) {
          myCollectionPanel.getTable().setRowSelectionInterval(selection, selection);
        }
      }
    });
  }

  protected static void performWriteCommandAction(final WriteCommandAction writeCommandAction) {
    writeCommandAction.execute();
  }

  public void commit() throws ReadOnlyDeploymentDescriptorModificationException {
    final CommitListener listener = myDispatcher.getMulticaster();
    listener.beforeCommit(this);
    listener.afterCommit(this);
  }

  public void dispose() {
    if (myCollectionPanel != null) {
      myCollectionPanel.dispose();
    }
  }

  protected final Project getProject() {
    return myParentDomElement.getManager().getProject();
  }

  public JComponent getFocusedComponent() {
    return getBoundComponent();
  }

  public JComponent getBoundComponent() {
    return getComponent();
  }

  public JComponent getComponent() {
    if (myCollectionPanel == null) initialize(null);

    return myCollectionPanel;
  }

  public final DomCollectionChildDescription getChildDescription() {
    return myChildDescription;
  }

  public final List<? extends T> getCollectionElements() {
    return (List<? extends T>)myChildDescription.getValues(myParentDomElement);
  }

  public final DomElement getDomElement() {
    return myParentDomElement;
  }

  public List<Warning> getWarnings() {
    return Collections.emptyList();
  }

  public final void reset() {
    myData.clear();
    myData.addAll(getData());
    if (myCollectionPanel != null) {
      final int row = myCollectionPanel.getTable().getSelectedRow();
      myCollectionPanel.getTableModel().fireTableDataChanged();
      if (row >= 0 && row < myData.size()) {
        myCollectionPanel.getTable().getSelectionModel().setSelectionInterval(row, row);
      }
    }
  }

  public List<T> getData() {
    return (List<T>)myChildDescription.getValues(myParentDomElement);
  }

  @NotNull
  protected AnAction[] createAdditionActions() {
    final ClassChooser chooser = ClassChooserManager.getClassChooser(getCollectionElementClass());
    return ContainerUtil.map2Array(chooser.getChooserClasses(), AnAction.class, new Function<Class, AnAction>() {
      public AnAction fun(final Class s) {
        return new DefaultAddAction(s.getSimpleName()) {
          protected Class<? extends T> getElementClass() {
            return s;
          }
        };
      }
    });
  }

  protected final Class<? extends T> getCollectionElementClass() {
    return (Class<? extends T>)DomUtil.getRawType(myChildDescription.getType());
  }


  @Nullable
  private static DomEditorManager getDomEditorManager(DomUIControl control) {
    JComponent component = control.getComponent();
    while (component != null && !(component instanceof DomEditorManager)) {
      final Container parent = component.getParent();
      if (!(parent instanceof JComponent)) {
        return null;
      }
      component = (JComponent)parent;
    }
    return (DomEditorManager)component;
  }

  protected class DefaultAddAction extends AnAction {

    public DefaultAddAction() {
    }

    public DefaultAddAction(String text) {
      super(text);
    }

    public DefaultAddAction(String text, String description, Icon icon) {
      super(text, description, icon);
    }

    protected Class<? extends T> getElementClass() {
      return getCollectionElementClass();
    }

    protected T doAdd() {
      return (T) myChildDescription.addValue(myParentDomElement, getElementClass());
    }

    protected boolean beforeAddition() {
      return true;
    }

    protected void afterAddition(final JTable table, final int rowIndex) {
      table.setRowSelectionInterval(rowIndex, rowIndex);
    }

    public void actionPerformed(AnActionEvent e) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (beforeAddition()) {
            final T newElement = new WriteCommandAction<T>(getProject()) {
              protected void run(Result<T> result) throws Throwable {
                result.setResult(doAdd());
              }
            }.execute().getResultObject();
            if (newElement != null) {
              reset();
              afterAddition(myCollectionPanel.getTable(), myData.size() - 1);
            }
          }
        }
      });
    }
  }

}
