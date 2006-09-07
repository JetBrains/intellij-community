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
package com.intellij.openapi.vfs;

import org.jetbrains.annotations.NotNull;

/**
 * Provides data for event which is fired when the name or writable status of a virtual file is changed.
 *
 * @see VirtualFileListener#beforePropertyChange(VirtualFilePropertyEvent)
 * @see VirtualFileListener#propertyChanged(VirtualFilePropertyEvent)
 */
public class VirtualFilePropertyEvent extends VirtualFileEvent {
  private final String myPropertyName;
  private final Object myOldValue;
  private final Object myNewValue;

  public VirtualFilePropertyEvent(Object requestor, VirtualFile file, String propertyName, Object oldValue, Object newValue){
    super(requestor, file, file.getName(), file.isDirectory(), file.getParent());
    myPropertyName = propertyName;
    myOldValue = oldValue;
    myNewValue = newValue;
  }

  /**
   * Returns the name of the changed property ({@link VirtualFile#PROP_NAME} or {@link VirtualFile#PROP_WRITABLE}).
   *
   * @return the name of the changed property.
   * @see VirtualFile#PROP_NAME
   * @see VirtualFile#PROP_WRITABLE
   */
  @NotNull
  public String getPropertyName() {
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
