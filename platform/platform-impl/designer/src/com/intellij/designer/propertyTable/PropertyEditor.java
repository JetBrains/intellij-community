// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer.propertyTable;

import com.intellij.designer.model.PropertiesContainer;
import com.intellij.designer.model.PropertyContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.EventListenerList;

/**
 * @author Alexander Lobas
 */
public abstract class PropertyEditor {
  private final EventListenerList myListenerList = new EventListenerList();

  public abstract @NotNull JComponent getComponent(@Nullable PropertiesContainer container,
                                                   @Nullable PropertyContext context,
                                                   Object value,
                                                   @Nullable InplaceContext inplaceContext);

  public @Nullable JComponent getPreferredFocusedComponent() {
    return null;
  }

  // is called when uer press a edit shortcut (F2). Editors may need to some actions, e.g. combobox should show popup.
  public void activate() {
  }

  public abstract @Nullable Object getValue() throws Exception;

  public abstract void updateUI();

  public final void addPropertyEditorListener(PropertyEditorListener listener) {
    myListenerList.add(PropertyEditorListener.class, listener);
  }

  public final void removePropertyEditorListener(PropertyEditorListener listener) {
    myListenerList.remove(PropertyEditorListener.class, listener);
  }

  public final void fireEditingCancelled() {
    for (PropertyEditorListener listener : myListenerList.getListeners(PropertyEditorListener.class)) {
      listener.editingCanceled(this);
    }
  }

  public final void fireValueCommitted(boolean continueEditing, boolean closeEditorOnError) {
    for (PropertyEditorListener listener : myListenerList.getListeners(PropertyEditorListener.class)) {
      listener.valueCommitted(this, continueEditing, closeEditorOnError);
    }
  }

  protected final void preferredSizeChanged() {
    for (PropertyEditorListener listener : myListenerList.getListeners(PropertyEditorListener.class)) {
      listener.preferredSizeChanged(this);
    }
  }
}