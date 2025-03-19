// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Provides data for an event that is fired when the name or writable status of a virtual file is changed.
 *
 * @see VirtualFileListener#beforePropertyChange(VirtualFilePropertyEvent)
 * @see VirtualFileListener#propertyChanged(VirtualFilePropertyEvent)
 */
public class VirtualFilePropertyEvent extends VirtualFileEvent {
  private final String myPropertyName;
  private final Object myOldValue;
  private final Object myNewValue;

  public VirtualFilePropertyEvent(Object requestor, @NotNull VirtualFile file, @NotNull String propertyName, Object oldValue, Object newValue){
    super(requestor, file, file.getParent(), 0, 0);
    myPropertyName = propertyName;
    myOldValue = oldValue;
    myNewValue = newValue;
    VFilePropertyChangeEvent.checkPropertyValuesCorrect(requestor, propertyName, oldValue, newValue);
  }

  /**
   * Returns the name of the changed property ({@link VirtualFile#PROP_NAME} or {@link VirtualFile#PROP_WRITABLE}).
   *
   * @return the name of the changed property.
   * @see VirtualFile#PROP_NAME
   * @see VirtualFile#PROP_WRITABLE
   * @see VirtualFile#PROP_HIDDEN
   * @see VirtualFile#PROP_SYMLINK_TARGET
   */
  public @NotNull String getPropertyName() {
    return myPropertyName;
  }

  /**
   * Returns the old value of the property.
   *
   * @return the old value of the property (String for {@link VirtualFile#PROP_NAME}, Boolean for
   * {@link VirtualFile#PROP_WRITABLE}).
   */
  public Object getOldValue() {
    return myOldValue;
  }

  /**
   * Returns the new value of the property.
   *
   * @return the new value of the property (String for {@link VirtualFile#PROP_NAME}, Boolean for
   * {@link VirtualFile#PROP_WRITABLE}).
   */
  public Object getNewValue() {
    return myNewValue;
  }
}