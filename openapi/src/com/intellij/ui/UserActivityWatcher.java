/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;

public class UserActivityWatcher {
  private boolean myIsModified = false;
  private ArrayList<UserActivityListener> myListeners = new ArrayList<UserActivityListener>();

  private final DocumentListener myDocumentListener = new DocumentAdapter() {
    public void textChanged(DocumentEvent event) {
      fireUIChanged();
    }
  };
  private final Class[] myControlsToIgnore;
  private TableModelListener myTableModelListener = new TableModelListener() {
    public void tableChanged(TableModelEvent e) {
      fireUIChanged();
    }
  };
  private PropertyChangeListener myTableListener = new PropertyChangeListener() {
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

  public void addUserActivityListener(UserActivityListener listener) {
    myListeners.add(listener);
  }

  public void removeUserActivityListener(UserActivityListener listener) {
    myListeners.remove(listener);
  }

  private void fireUIChanged() {
    myIsModified = true;
    UserActivityListener[] listeners = myListeners.toArray(new UserActivityListener[myListeners.size()]);
    for (int i = 0; i < listeners.length; i++) {
      UserActivityListener listener = listeners[i];
      listener.stateChanged();
    }
  }

  private final ItemListener myItemListener = new ItemListener() {
    public void itemStateChanged(ItemEvent e) {
      fireUIChanged();
    }
  };
  private final ContainerListener myContainerListener = new ContainerListener() {
    public void componentAdded(ContainerEvent e) {
      register(e.getChild());
    }

    public void componentRemoved(ContainerEvent e) {
      unregister(e.getChild());
    }
  };

  public UserActivityWatcher(Class[] controlsToIgnore) {
    myControlsToIgnore = controlsToIgnore;
  }

  public UserActivityWatcher() {
    this(new Class[0]);

  }

  private boolean shouldBeIgnored(Object object) {
    if (object instanceof CellRendererPane) return true;
    if (object == null) {
      return true;
    }
    for (int i = 0; i < myControlsToIgnore.length; i++) {
      Class aClass = myControlsToIgnore[i];
      if (aClass.isAssignableFrom(object.getClass())) {
        return true;
      }
    }
    return false;
  }

  public void register(Component parentComponent) {
    if (shouldBeIgnored(parentComponent)) {
      return;
    }
    if (parentComponent instanceof JTextComponent) {
      ((JTextComponent)parentComponent).getDocument().addDocumentListener(myDocumentListener);
    }
    else if (parentComponent instanceof ItemSelectable) {
      ((ItemSelectable)parentComponent).addItemListener(myItemListener);
    }
    else if (parentComponent instanceof Container) {
      Container container = ((Container)parentComponent);
      for (int i = 0; i < container.getComponentCount(); i++) {
        register(container.getComponent(i));
      }
      container.addContainerListener(myContainerListener);
    }

    if (parentComponent instanceof JComboBox) {
      ComboBoxEditor editor = ((JComboBox)parentComponent).getEditor();
      if (editor != null) {
        register(editor.getEditorComponent());
      }
    }

    if (parentComponent instanceof JTable) {
      JTable table = (JTable)parentComponent;
      //noinspection HardCodedStringLiteral
      table.addPropertyChangeListener("model", myTableListener);
      TableModel model = table.getModel();
      if (model != null) {
        model.addTableModelListener(myTableModelListener);
      }
    }
  }

  private void unregister(Component component) {
    if (component instanceof JTextComponent) {
      ((JTextComponent)component).getDocument().removeDocumentListener(myDocumentListener);
    }
    else if (component instanceof ItemSelectable) {
      ((ItemSelectable)component).removeItemListener(myItemListener);
    }
    else if (component instanceof Container) {
      Container container = ((Container)component);
      for (int i = 0; i < container.getComponentCount(); i++) {
        unregister(container.getComponent(i));
      }
      container.removeContainerListener(myContainerListener);
    }

    if (component instanceof JTable) {
      component.removePropertyChangeListener(myTableListener);
      TableModel model = ((JTable)component).getModel();
      if (model != null) {
        model.removeTableModelListener(myTableModelListener);
      }
    }
  }

  public boolean isModified() {
    return myIsModified;
  }

  public void commit() {
    myIsModified = false;
  }
}