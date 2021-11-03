// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Key;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.TableModel;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class UserActivityWatcher extends ComponentTreeWatcher {
  @ApiStatus.Experimental
  public static final Key<Boolean> DO_NOT_WATCH = Key.create("DO_NOT_WATCH");
  private boolean myIsModified = false;
  private final EventDispatcher<UserActivityListener> myListeners = EventDispatcher.create(UserActivityListener.class);

  private final static String TABLE_CELL_EDITOR_PROPERTY = "tableCellEditor"; // ComboBox.TABLE_CELL_EDITOR_PROPERTY

  private final DocumentListener myDocumentListener = new DocumentAdapter() {
    @Override
    public void textChanged(@NotNull DocumentEvent event) {
      fireUIChanged();
    }
  };

  private final com.intellij.openapi.editor.event.DocumentListener myIdeaDocumentListener = new com.intellij.openapi.editor.event.DocumentListener() {
    @Override
    public void documentChanged(@NotNull final com.intellij.openapi.editor.event.DocumentEvent e) {
      fireUIChanged();
    }
  };

  private final TableModelListener myTableModelListener = new TableModelListener() {
    @Override
    public void tableChanged(TableModelEvent e) {
      fireUIChanged();
    }
  };

  private final PropertyChangeListener myTableListener = new PropertyChangeListener() {
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      TableModel oldModel = (TableModel)evt.getOldValue();
      if (oldModel != null) {
        oldModel.removeTableModelListener(myTableModelListener);
      }

      TableModel newModel = (TableModel)evt.getNewValue();
      if (newModel != null) {
        newModel.addTableModelListener(myTableModelListener);
      }

      if (oldModel != null) {
        fireUIChanged();
      }
    }
  };

  private final ChangeListener myChangeListener = new ChangeListener() {
    @Override
    public void stateChanged(final ChangeEvent e) {
      fireUIChanged();
    }
  };

  private final PropertyChangeListener myCellEditorChangeListener = new PropertyChangeListener() {
    @Override
    public void propertyChange(PropertyChangeEvent e) {
      if (e.getOldValue() != null && e.getNewValue() == null) fireUIChanged();
    }
  };

  public void addUserActivityListener(UserActivityListener listener) {
    myListeners.addListener(listener);
  }

  public void addUserActivityListener(UserActivityListener listener, Disposable parentDisposable) {
    myListeners.addListener(listener, parentDisposable);
  }

  public void removeUserActivityListener(UserActivityListener listener) {
    myListeners.removeListener(listener);
  }

  @Override
  protected boolean shouldBeIgnored(Object object) {
    if (Boolean.TRUE.equals(UIUtil.getClientProperty(object, DO_NOT_WATCH))) return true;
    return super.shouldBeIgnored(object);
  }

  protected final void fireUIChanged() {
    myIsModified = true;
    myListeners.getMulticaster().stateChanged();
  }

  private final ItemListener myItemListener = new ItemListener() {
    @Override
    public void itemStateChanged(ItemEvent e) {
      fireUIChanged();
    }
  };

  private final ListDataListener myListDataListener = new ListDataListener() {
    @Override
    public void intervalAdded(ListDataEvent e) {
      fireUIChanged();
    }

    @Override
    public void intervalRemoved(ListDataEvent e) {
      fireUIChanged();
    }

    @Override
    public void contentsChanged(ListDataEvent e) {
      fireUIChanged();
    }
  };

  private final ListSelectionListener myListSelectionListener = new ListSelectionListener() {

    @Override
    public void valueChanged(ListSelectionEvent e) {
      fireUIChanged();
    }
  };

  private final TreeModelListener myTreeModelListener = new TreeModelListener() {
    @Override
    public void treeNodesChanged(final TreeModelEvent e) {
      fireUIChanged();
    }

    @Override
    public void treeNodesInserted(final TreeModelEvent e) {
      fireUIChanged();
    }

    @Override
    public void treeNodesRemoved(final TreeModelEvent e) {
      fireUIChanged();
    }

    @Override
    public void treeStructureChanged(final TreeModelEvent e) {
      fireUIChanged();
    }
  };


  public UserActivityWatcher(Class[] controlsToIgnore) {
    super(controlsToIgnore);
  }

  public UserActivityWatcher() {
    this(ArrayUtil.EMPTY_CLASS_ARRAY);

  }

  @Override
  protected boolean processChildren(Container container) {
    return !(container instanceof JTable);
  }

  @Override
  protected void processComponent(final Component parentComponent) {
    if (parentComponent instanceof JTextComponent) {
      ((JTextComponent)parentComponent).getDocument().addDocumentListener(myDocumentListener);
    }
    else if (parentComponent instanceof EditorTextComponent) {
      ((EditorTextComponent)parentComponent).addDocumentListener(myIdeaDocumentListener);
    }
    else if (parentComponent instanceof ItemSelectable) {
      ((ItemSelectable)parentComponent).addItemListener(myItemListener);
    }
    else if (parentComponent instanceof JList) {
      ((JList)parentComponent).getModel().addListDataListener(myListDataListener);
      if (trackListSelection()) ((JList<?>)parentComponent).addListSelectionListener(myListSelectionListener);
    }
    else if (parentComponent instanceof JTree) {
      ((JTree)parentComponent).getModel().addTreeModelListener(myTreeModelListener);
    }

    if (parentComponent instanceof JComboBox) {
      ComboBoxEditor editor = ((JComboBox<?>)parentComponent).getEditor();
      if (editor != null) {
        register(editor.getEditorComponent());
      }
    }

    if (parentComponent instanceof JTable) {
      JTable table = (JTable)parentComponent;
      table.addPropertyChangeListener("model", myTableListener);
      TableModel model = table.getModel();
      if (model != null) {
        model.addTableModelListener(myTableModelListener);
      }
      table.addPropertyChangeListener(TABLE_CELL_EDITOR_PROPERTY, myCellEditorChangeListener);
    }

    if (parentComponent instanceof JSlider) {
      ((JSlider)parentComponent).addChangeListener(myChangeListener);
    }

    UserActivityProviderComponent provider = getActivityProviderComponent(parentComponent);
    if (provider != null) {
      provider.addChangeListener(myChangeListener);
    }
  }

  protected boolean trackListSelection() {
    return true;
  }

  @Override
  protected void unprocessComponent(final Component component) {
    if (component instanceof JTextComponent) {
      ((JTextComponent)component).getDocument().removeDocumentListener(myDocumentListener);
    }
    else if (component instanceof EditorTextComponent) {
      ((EditorTextComponent)component).removeDocumentListener(myIdeaDocumentListener);
    }
    else if (component instanceof ItemSelectable) {
      ((ItemSelectable)component).removeItemListener(myItemListener);
    }
    else if (component instanceof JTree) {
      ((JTree)component).getModel().removeTreeModelListener(myTreeModelListener);
    }

    if (component instanceof JTable) {
      component.removePropertyChangeListener(myTableListener);
      TableModel model = ((JTable)component).getModel();
      if (model != null) {
        model.removeTableModelListener(myTableModelListener);
      }
      component.removePropertyChangeListener(myCellEditorChangeListener);
    }

    if (component instanceof JSlider){
      ((JSlider)component).removeChangeListener(myChangeListener);
    }

    UserActivityProviderComponent provider = getActivityProviderComponent(component);
    if (provider != null) {
      provider.removeChangeListener(myChangeListener);
    }
  }

  private static UserActivityProviderComponent getActivityProviderComponent(Component component) {
    return component instanceof UserActivityProviderComponent
           ? (UserActivityProviderComponent)component
           : UIUtil.getClientProperty(component, UserActivityProviderComponent.class);
  }

  public boolean isModified() {
    return myIsModified;
  }

  public void commit() {
    myIsModified = false;
  }

  public static class UserActivityProvider implements UserActivityProviderComponent, ChangeListener {
    private final EventDispatcher<ChangeListener> myDispatcher = EventDispatcher.create(ChangeListener.class);
    @Override
    public void addChangeListener(@NotNull ChangeListener changeListener) {
      myDispatcher.addListener(changeListener);
    }

    @Override
    public void removeChangeListener(@NotNull ChangeListener changeListener) {
      myDispatcher.removeListener(changeListener);
    }

    @Override
    public void stateChanged(ChangeEvent e) {
      myDispatcher.getMulticaster().stateChanged(e);
    }

    public void stateChanged(Object source) {
      stateChanged(new ChangeEvent(source));
    }

    public static UserActivityProvider create(JComponent component) {
      UserActivityProvider provider = new UserActivityProvider();
      component.putClientProperty(UserActivityProviderComponent.class, provider);
      return provider;
    }
  }
}
