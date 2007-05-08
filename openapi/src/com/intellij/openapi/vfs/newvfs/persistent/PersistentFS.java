/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.*;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.VFileImpl;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.DupOutputStream;
import com.intellij.util.io.LimitedInputStream;
import com.intellij.util.io.ReplicatorInputStream;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Collections;
import java.util.List;

public class PersistentFS extends ManagingFS {
  private static final Logger LOG = Logger.getInstance("#com.intellij.vfs.persistent.PersistentFS");

  private final static FileAttribute FILE_CONTENT = new FileAttribute("PersistentFS.File.Contents", 1);

  private static final int CHILDREN_CACHED_FLAG = 0x01;
  private static final int IS_DIRECTORY_FLAG = 0x02;
  private static final int IS_READ_ONLY = 0x04;

  private NewVirtualFileSystem myDelegate;
  private final VirtualFile myRoot;
  private FSRecords myRecords;
  private final String myBaseUrl;
  private final MessageBus myEventsBus;

  public PersistentFS(VirtualFile root, MessageBus bus) throws IOException {
    myEventsBus = bus;
    myDelegate = (NewVirtualFileSystem)root.getFileSystem();
    myRoot = root;
    try {
      myRecords = new FSRecords(myRoot.getUrl());
      myRecords.connect();
    }
    catch (IOException e) {
      LOG.info("Rebuilding FS repository for '" + myRoot.getUrl() + "'. Reason: " + e.getMessage());
      myRecords.disposeAndDeleteFiles();
      myRecords.connect();
    }

    myBaseUrl = root.getUrl();
  }

  public NewVirtualFileSystem getDelegate() {
    return myDelegate;
  }

  public boolean areChildrenLoaded(final VirtualFile dir) {
    return areChildrenLoaded(getFileId(dir));
  }

  public String[] list(final VirtualFile file) {
    int id = getFileId(file);

    String[] names;
    final int[] childrenIds;
    if (areChildrenLoaded(id)) {
      childrenIds = myRecords.list(id);
      names = new String[childrenIds.length];
      for (int i = 0; i < childrenIds.length; i++) {
        names[i] = myRecords.getName(childrenIds[i]);
      }
    }
    else {
      names = myDelegate.list(file);
      childrenIds = new int[names.length];

      for (int i = 0; i < names.length; i++) {
        int childId = myRecords.createRecord();
        VFileImpl fakeFile = new VFileImpl(names[i], file, myDelegate, childId);
        copyRecordFromDelegateFS(childId, id, fakeFile);
        childrenIds[i] = childId;
      }

      myRecords.updateList(id, childrenIds);
      int flags = myRecords.getFlags(id);
      myRecords.setFlags(id, flags | CHILDREN_CACHED_FLAG);
    }

    return names;
  }

  public VirtualFile[] listFiles(final VirtualFile parent) {
    final int parentId = getFileId(parent);

    if (!areChildrenLoaded(parentId)) {
      list(parent);
    }

    final int[] childrenIds = myRecords.list(parentId);
    VirtualFile[] children = new VirtualFile[childrenIds.length];
    for (int i = 0; i < children.length; i++) {
      final int childId = childrenIds[i];
      children[i] = ((VFileImpl)parent).createChildFileInstance(myRecords.getName(childId), childId);
    }

    return children;
  }

  public boolean areChildrenLoaded(final int parentId) {
    int flags = myRecords.getFlags(parentId);
    return (flags & CHILDREN_CACHED_FLAG) != 0;
  }

  @Nullable
  public DataInputStream readAttribute(final VirtualFile file, final FileAttribute att) {
    return myRecords.readAttribute(getFileId(file), att.getId());
  }

  public DataOutputStream writeAttribute(final VirtualFile file, final FileAttribute att) {
    return (DataOutputStream)myRecords.writeAttribute(getFileId(file), att.getId());
  }

  public int getModificationCount(final VirtualFile file) {
    final int id = getFileId(file);
    return myRecords.getModCount(id);
  }

  public int getFilesystemModificationCount() {
    return myRecords.getModCount();
  }

  private void copyRecordFromDelegateFS(final int id, final int parentId, final VirtualFile file) {
    String name = file.getName();

    if (name.length() > 0 && namesEqual(name, myRecords.getName(id))) return; // TODO: Handle root attributes change.

    if (name.length() == 0) {            // TODO: hack
      if (areChildrenLoaded(1)) return;
    }

    myRecords.setParent(id, parentId);
    myRecords.setName(id, name);

    myRecords.setCRC(id, myDelegate.getCRC(file));
    myRecords.setTimestamp(id, myDelegate.getTimeStamp(file));
    myRecords.setFlags(id, (myDelegate.isDirectory(file) ? IS_DIRECTORY_FLAG : 0) |
                           (!myDelegate.isWritable(file) ? IS_READ_ONLY : 0));

    myRecords.setLength(id, -1);

    // TODO!!!: More attributes?
  }

  public boolean isDirectory(final VirtualFile file) {
    final int id = getFileId(file);
    return (myRecords.getFlags(id) & IS_DIRECTORY_FLAG) != 0;
  }

  private boolean namesEqual(String n1, String n2) {
    return isCaseSensitive() ? n1.equals(n2) : n1.equalsIgnoreCase(n2);
  }

  public boolean isCaseSensitive() {
    return myDelegate.isCaseSensitive();
  }

  @NonNls
  public String getProtocol() {
    return myDelegate.getProtocol();
  }

  public VirtualFile getRoot() {
    return myRoot;
  }

  @NonNls
  public String getBaseUrl() {
    return myBaseUrl;
  }

  public boolean exists(final VirtualFile fileOrDirectory) {
    return ((NewVirtualFile)fileOrDirectory).getId() != 0;
  }

  public long getTimeStamp(final VirtualFile file) {
    final int id = getFileId(file);
    return myRecords.getTimestamp(id);
  }

  public void setTimeStamp(final VirtualFile file, final long modstamp) throws IOException {
    final int id = getFileId(file);

    myRecords.setTimestamp(id, modstamp);
    myDelegate.setTimeStamp(file, modstamp);
  }

  private static int getFileId(final VirtualFile file) {
    final int id = ((NewVirtualFile)file).getId();
    assert id > 0;
    return id;
  }

  public boolean isWritable(final VirtualFile file) {
    final int id = getFileId(file);

    return (myRecords.getFlags(id) & IS_READ_ONLY) == 0;
  }

  public void setWritable(final VirtualFile file, final boolean writableFlag) throws IOException {
    myDelegate.setWritable(file, writableFlag);
    processEvent(new VFilePropertyChangeEvent(file, VirtualFile.PROP_WRITABLE, isWritable(file), writableFlag, false));
  }

  public long getCRC(final VirtualFile file) {
    final int id = getFileId(file);

    return myRecords.getCRC(id);
  }

  public long getLength(final VirtualFile file) {
    final int id = getFileId(file);

    int len = myRecords.getLength(id);
    if (len == -1) {
      len = (int)myDelegate.getLength(file);
      myRecords.setLength(id, len);
    }

    return len;
  }

  public int getId(final VirtualFile parent, final String childName) {
    final int parentId = parent != null ? ((NewVirtualFile)parent).getId() : 0;
    if (parentId == 0) {
      copyRecordFromDelegateFS(1, 0, new VFileImpl(childName, parent, myDelegate, 1));
      return 1;
    }
    else {
      if (!areChildrenLoaded(parentId)) {
        list(parent);
      }

      for (final int childId : myRecords.list(parentId)) {
        if (namesEqual(childName, myRecords.getName(childId))) return childId;
      }

      return 0;
    }
  }

  public VirtualFile copyFile(final Object requestor, final VirtualFile from, final VirtualFile newParent, final String copyName) throws IOException {
    myDelegate.copyFile(requestor, from, newParent, copyName);
    processEvent(new VFileCopyEvent(from, newParent, copyName));
    final VirtualFile child = newParent.findChild(copyName);
    if (child == null) {
      throw new IOException("Cannot create child");
    }
    return child;
  }

  public VirtualFile createChildDirectory(final Object requestor, final VirtualFile parent, final String dir) throws IOException {
    myDelegate.createChildDirectory(requestor, parent, dir);
    processEvent(new VFileCreateEvent(parent, dir, true, false));
    return parent.findChild(dir);
  }

  public VirtualFile createChildFile(final Object requestor, final VirtualFile parent, final String file) throws IOException {
    myDelegate.createChildFile(requestor, parent, file);
    processEvent(new VFileCreateEvent(parent, file, false, false));
    return parent.findChild(file);
  }

  public void deleteFile(final Object requestor, final VirtualFile file) throws IOException {
    myDelegate.deleteFile(requestor, file);
    processEvent(new VFileDeleteEvent(file, false));
  }

  public void renameFile(final Object requestor, final VirtualFile from, final String newName) throws IOException {
    myDelegate.renameFile(requestor, from, newName);
    processEvent(new VFilePropertyChangeEvent(from, VirtualFile.PROP_NAME, from.getName(), newName, false));
  }

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  public InputStream getInputStream(final VirtualFile file) throws FileNotFoundException {
    try {
      InputStream contentStream = FILE_CONTENT.readAttribute(file);
      if (contentStream == null) {
        final int len = (int)myDelegate.getLength(file);

        return new ReplicatorInputStream(myDelegate.getInputStream(file), new BufferedOutputStream(FILE_CONTENT.writeAttribute(file))) {
          public void close() throws IOException {
            super.close();
            myRecords.setLength(getFileId(file), len);
          }
        };
      }
      else {
        return new BufferedInputStream(new LimitedInputStream(contentStream, myRecords.getLength(getFileId(file))));
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  public OutputStream getOutputStream(final VirtualFile file, final Object requestor, final long modStamp, final long timeStamp) throws IOException {
    return new DupOutputStream(new BufferedOutputStream(FILE_CONTENT.writeAttribute(file)), myDelegate.getOutputStream(file, requestor,
                                                                                                                       modStamp, timeStamp)) {
      public void close() throws IOException {
        super.close();
        executeTouch(file, false);
      }
    };
  }

  public String extractPresentableUrl(final String path) {
    return myDelegate.extractPresentableUrl(path);
  }

  public void moveFile(final Object requestor, final VirtualFile from, final VirtualFile newParent) throws IOException {
    if (!from.exists()) {
      throw new IOException("File to move does not exist: " + from.getPath());
    }

    if (!newParent.exists()) {
      throw new IOException("Destination folder does not exist: " + newParent.getPath());
    }

    if (!newParent.isDirectory()) {
      throw new IOException("Destination is not a folder: " + newParent.getPath());
    }

    final VirtualFile child = newParent.findChild(from.getName());
    if (child != null) {
      throw new IOException("Destination already exists: " + newParent.getPath() + "/" + from.getName());
    }

    processEvent(new VFileMoveEvent(from, newParent));
  }

  private void processEvent(VFileEvent event) {
    processEvents(Collections.singletonList(event));
  }

  public void processEvents(List<VFileEvent> events) {
    myEventsBus.syncPublisher(VirtualFileManagement.VFS_CHANGES).before(events);
    for (VFileEvent event : events) {
      applyEvent(event);
    }
    myEventsBus.syncPublisher(VirtualFileManagement.VFS_CHANGES).after(events);
  }

  private void applyEvent(final VFileEvent event) {
    if (event instanceof VFileCreateEvent) {
      final VFileCreateEvent createEvent = (VFileCreateEvent)event;
      executeCreateChild(createEvent.getParent(), createEvent.getChildName());
    }
    else if (event instanceof VFileDeleteEvent) {
      final VFileDeleteEvent deleteEvent = (VFileDeleteEvent)event;
      executeDelete(deleteEvent.getFile());
    }
    else if (event instanceof VFileContentChangeEvent) {
      final VFileContentChangeEvent contentUpdateEvent = (VFileContentChangeEvent)event;
      executeTouch(contentUpdateEvent.getFile(), contentUpdateEvent.isFromRefresh());
    }
    else if (event instanceof VFileCopyEvent) {
      final VFileCopyEvent copyEvent = (VFileCopyEvent)event;
      executeCopy(copyEvent.getFile(), copyEvent.getNewParent(), copyEvent.getNewChildName());
    }
    else if (event instanceof VFileMoveEvent) {
      final VFileMoveEvent moveEvent = (VFileMoveEvent)event;
      executeMove(moveEvent.getFile(), moveEvent.getNewParent());
    }
    else if (event instanceof VFilePropertyChangeEvent) {
      final VFilePropertyChangeEvent propertyChangeEvent = (VFilePropertyChangeEvent)event;
      if (VirtualFile.PROP_NAME.equals(propertyChangeEvent.getPropertyName())) {
        executeRename(propertyChangeEvent.getFile(), (String)propertyChangeEvent.getNewValue());
      }
      else if (VirtualFile.PROP_WRITABLE.equals(propertyChangeEvent.getPropertyName())) {
        executeSetWritable(propertyChangeEvent.getFile(), ((Boolean)propertyChangeEvent.getNewValue()).booleanValue());
      }
    }
  }

  public void dispose() {
    myRecords.dispose();
  }

  @NonNls
  public String toString() {
    return "PersistentFS[" + getBaseUrl() + "]";
  }

  private void executeCreateChild(final VirtualFile parent, final String name) {
    VFileImpl fakeFile = new VFileImpl(name, parent, myDelegate, 0);
    if (fakeFile.exists()) {
      final int parentId = getFileId(parent);
      int childId = myRecords.createRecord();
      copyRecordFromDelegateFS(childId, parentId, fakeFile);

      int[] childrenlist = myRecords.list(parentId);
      childrenlist = ArrayUtil.append(childrenlist, childId);
      myRecords.updateList(parentId, childrenlist);

      ((VFileImpl)parent).createChildFileInstance(name, childId);
    }
  }

  private void executeDelete(final VirtualFile file) {
    final int id = getFileId(file);

    final VirtualFile parent = file.getParent();
    final int parentId = getFileId(parent);

    myRecords.deleteRecordRecursively(id);

    int[] childList = myRecords.list(parentId);
    childList = ArrayUtil.remove(childList, ArrayUtil.indexOf(childList, id));
    myRecords.updateList(parentId, childList);
  }

  private void executeRename(final VirtualFile file, final String newName) {
    final int id = getFileId(file);
    myRecords.setName(id, newName);
    ((VFileImpl)file).setName(newName);
  }

  private void executeSetWritable(final VirtualFile file, final boolean writableFlag) {
    final int id = getFileId(file);

    int flags = myRecords.getFlags(id);
    if (writableFlag) {
      flags &= ~IS_READ_ONLY;
    }
    else {
      flags |= IS_READ_ONLY;
    }
    myRecords.setFlags(id, flags);
  }

  private void executeTouch(final VirtualFile file, boolean reloadContentFromDelegate) {
    if (reloadContentFromDelegate) {
      reloadContentFromDelegate(file);
    }

    myRecords.setLength(getFileId(file), (int)myDelegate.getLength(file));
    myRecords.setTimestamp(getFileId(file), myDelegate.getTimeStamp(file));
  }

  private void executeCopy(final VirtualFile from, final VirtualFile newParent, final String copyName) {
    throw new UnsupportedOperationException("copy is not implemented"); // TODO
  }

  private void executeMove(final VirtualFile from, final VirtualFile newParent) {
    final NewVirtualFileSystem destinationFS = VirtualFileManagement.getInstance().findFSForChild(newParent, from.getName());
    if (destinationFS == this) {
      ((VFileImpl)from).setParent(newParent);
    }
    else {
      throw new UnsupportedOperationException("copy not supported"); // TODO:!
    }
  }

  private void reloadContentFromDelegate(final VirtualFile file) {
    final DataInputStream currentContent = FILE_CONTENT.readAttribute(file);
    if (currentContent != null) {
      try {
        currentContent.close();

        InputStream is = null;
        OutputStream os = null;
        try {
          is = myDelegate.getInputStream(file);
          os = new BufferedOutputStream(FILE_CONTENT.writeAttribute(file));
          FileUtil.copy(is, os);
        }
        finally {
          if (is != null) is.close();
          if (os != null) os.close();
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}