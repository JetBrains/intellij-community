/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.VFileImpl;
import com.intellij.util.containers.Stack;

import java.util.*;

public class RefreshRequest {
  private boolean myIsRecursive;
  private Stack<VirtualFile> myRefreshQueue = new Stack<VirtualFile>();
  private ManagingFS myVFS;

  private List<VFileEvent> myEvents = new ArrayList<VFileEvent>();

  public RefreshRequest(final VirtualFile refreshRoot, final boolean isRecursive) {
    myIsRecursive = isRecursive;
    myRefreshQueue.push(refreshRoot);
    myVFS = (ManagingFS)refreshRoot.getFileSystem();
  }

  public void scan() {
    while (!myRefreshQueue.isEmpty()) {
      final VFileImpl file = (VFileImpl)myRefreshQueue.pop();

      if (file.isDirectory()) {
        final boolean syncDir = myVFS.areChildrenLoaded(file);
        if (syncDir) {
          Set<String> currentNames = new HashSet<String>(Arrays.asList(myVFS.list(file)));
          Set<String> uptodateNames = new HashSet<String>(Arrays.asList(myVFS.getDelegate().list(file)));

          Set<String> newNames = new HashSet<String>(uptodateNames);
          newNames.removeAll(currentNames);

          Set<String> deletedNames = new HashSet<String>(currentNames);
          deletedNames.removeAll(uptodateNames);

          for (String name : deletedNames) {
            scheduleDeletion(file.findChild(name));
          }

          for (String name : newNames) {
            boolean isDirectory = new VFileImpl(name, file, myVFS.getDelegate(), 0).isDirectory();
            scheduleCreation(file, name, isDirectory);
          }

          for (VirtualFile child : file.getChildren()) {
            if (!deletedNames.contains(child.getName()) && child.getFileSystem() == myVFS) {
              final boolean currentIsDirectory = child.isDirectory();
              final boolean uptodateisDirectory = myVFS.getDelegate().isDirectory(child);
              if (currentIsDirectory != uptodateisDirectory) {
                scheduleDeletion(child);
                scheduleCreation(file, child.getName(), uptodateisDirectory);
              }
              else if ((myIsRecursive || !currentIsDirectory)) {
                myRefreshQueue.push(child);
              }
            }
          }
        }
      }
      else {
        long currentTimestamp = myVFS.getTimeStamp(file);
        long updtodateTimestamp = myVFS.getDelegate().getTimeStamp(file);

        if (currentTimestamp != updtodateTimestamp) {
          scheduleUpdateContent(file, currentTimestamp, updtodateTimestamp);
        }

        boolean currentReadOnly = !myVFS.isWritable(file);
        boolean uptodateReadOnly = !myVFS.getDelegate().isWritable(file);

        if (currentReadOnly != uptodateReadOnly) {
          scheduleReadOnlyAttributeChange(file, currentReadOnly, uptodateReadOnly);
        }
      }
    }
  }

  private void scheduleReadOnlyAttributeChange(final VFileImpl file, final boolean currentReadOnly, final boolean uptodateReadOnly) {
    myEvents.add(new VFilePropertyChangeEvent(file, VirtualFile.PROP_WRITABLE, currentReadOnly, uptodateReadOnly, true));
  }

  private void scheduleUpdateContent(final VFileImpl file, long oldTimestamp, long newTimestamp) {
    myEvents.add(new VFileContentChangeEvent(file, oldTimestamp, newTimestamp, true));
  }

  private void scheduleCreation(final VFileImpl parent, final String childName, final boolean isDirectory) {
    myEvents.add(new VFileCreateEvent(parent, childName, isDirectory, true));
  }

  private void scheduleDeletion(final VirtualFile file) {
    myEvents.add(new VFileDeleteEvent(file, true));
  }

  public List<VFileEvent> getEvents() {
    return myEvents;
  }
}