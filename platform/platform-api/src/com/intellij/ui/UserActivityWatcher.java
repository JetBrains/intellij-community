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
package com.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EventDispatcher;

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
  private boolean myIsModified = false;
  private final EventDispatcher<UserActivityListener> myListeners = EventDispatcher.create(UserActivityListener.class);

  private final DocumentListener myDocumentListener = new DocumentAdapter() {
    public void textChanged(DocumentEvent event) {
      fireUIChanged();
    }
  };

  private final com.intellij.openapi.editor.event.DocumentListener myIdeaDocumentListener = new com.intellij.openapi.editor.event.DocumentAdapter() {
    @Override
    public void documentChanged(final com.intellij.openapi.editor.event.DocumentEvent e) {
      fireUIChanged();
    }
  };

  private final TableModelListener myTableModelListener = new TableModelListener() {
    public void tableChanged(TableModelEvent e) {
      fireUIChanged();
    }
  };
  private final PropertyChangeListener myTableListener = new PropertyChangeListener() {
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
    public void stateChanged(final ChangeEvent e) {
      fireUIChanged();
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

  protected final void fireUIChanged() {
    myIsModified = true;
    myListeners.getMulticaster().stateChanged();
  }

  private final ItemListener myItemListener = new ItemListener() {
    public void itemStateChanged(ItemEvent e) {
      fireUIChanged();
    }
  };

  private final ListDataListener myListDataListener = new ListDataListener() {
    public void intervalAdded(ListDataEvent e) {
      fireUIChanged();
    }

    public void intervalRemoved(ListDataEvent e) {
      fireUIChanged();
    }

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
    public void treeNodesChanged(final TreeModelEvent e) {
      fireUIChanged();
    }

    public void treeNodesInserted(final TreeModelEvent e) {
      fireUIChanged();
    }

    public void treeNodesRemoved(final TreeModelEvent e) {
      fireUIChanged();
    }

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

  protected void processComponent(final Component parentComponent) {
    if (parentComponent instanceof JTextComponent) {
      ((JTextComponent)parentComponent).getDocument().addDocumentListener(myDocumentListener);
    }
    else if (parentComponent instanceof ItemSelectable) {
      ((ItemSelectable)parentComponent).addItemListener(myItemListener);
    }
    else if (parentComponent instanceof JList) {
      ((JList)parentComponent).getModel().addListDataListener(myListDataListener);
      ((JList)parentComponent).addListSelectionListener(myListSelectionListener);
    } else if (parentComponent instanceof JTree) {
      ((JTree)parentComponent).getModel().addTreeModelListener(myTreeModelListener);
    } else if (parentComponent instanceof DocumentBasedComponent) {
      ((DocumentBasedComponent)parentComponent).getDocument().addDocumentListener(myIdeaDocumentListener);
    }

    if (parentComponent instanceof JComboBox) {
      ComboBoxEditor editor = ((JComboBox)parentComponent).getEditor();
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
    }

    if (parentComponent instanceof JSlider) {
      ((JSlider)parentComponent).addChangeListener(myChangeListener);
    }

    if (parentComponent instanceof UserActivityProviderComponent) {
      ((UserActivityProviderComponent)parentComponent).addChangeListener(myChangeListener);
    }
  }

  protected void unprocessComponent(final Component component) {
    if (component instanceof JTextComponent) {
      ((JTextComponent)component).getDocument().removeDocumentListener(myDocumentListener);
    }
    else if (component instanceof ItemSelectable) {
      ((ItemSelectable)component).removeItemListener(myItemListener);
    } else if (component instanceof JTree) {
      ((JTree)component).getModel().removeTreeModelListener(myTreeModelListener);
    } else if (component instanceof DocumentBasedComponent) {
      ((DocumentBasedComponent)component).getDocument().removeDocumentListener(myIdeaDocumentListener);
    }

    if (component instanceof JTable) {
      component.removePropertyChangeListener(myTableListener);
      TableModel model = ((JTable)component).getModel();
      if (model != null) {
        model.removeTableModelListener(myTableModelListener);
      }
    }

    if (component instanceof JSlider){
      ((JSlider)component).removeChangeListener(myChangeListener);
    }

    if (component instanceof UserActivityProviderComponent) {
      ((UserActivityProviderComponent)component).removeChangeListener(myChangeListener);
    }
  }

  public boolean isModified() {
    return myIsModified;
  }

  public void commit() {
    myIsModified = false;
  }
}
