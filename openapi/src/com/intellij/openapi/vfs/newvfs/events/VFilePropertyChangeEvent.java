/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NonNls;

public class VFilePropertyChangeEvent extends VFileEvent {
  private VirtualFile myFile;
  private final String myPropertyName;
  private final Object myOldValue;
  private final Object myNewValue;

  public VFilePropertyChangeEvent(Object requestor, final VirtualFile file, String propertyName, Object oldValue, Object newValue, boolean isFromRefresh) {
    super(requestor, isFromRefresh);
    myFile = file;
    myPropertyName = propertyName;
    myOldValue = oldValue;
    myNewValue = newValue;
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public Object getNewValue() {
    return myNewValue;
  }

  public Object getOldValue() {
    return myOldValue;
  }

  public String getPropertyName() {
    return myPropertyName;
  }

  @NonNls
  public String toString() {
    return "VfsEvent[property( " + myPropertyName + ") changed for '" + myFile + "': oldValue = " + myOldValue + ", newValue = " + myNewValue + "]";
  }

  public String getPath() {
    return myFile.getPath();
  }

  public VirtualFileSystem getFileSystem() {
    return myFile.getFileSystem();
  }

  public boolean isValid() {
    return myFile.isValid();
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VFilePropertyChangeEvent event = (VFilePropertyChangeEvent)o;

    if (!myFile.equals(event.myFile)) return false;
    if (!myNewValue.equals(event.myNewValue)) return false;
    if (!myOldValue.equals(event.myOldValue)) return false;
    if (!myPropertyName.equals(event.myPropertyName)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myFile.hashCode();
    result = 31 * result + myPropertyName.hashCode();
    result = 31 * result + myOldValue.hashCode();
    result = 31 * result + myNewValue.hashCode();
    return result;
  }
}