package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.io.PrintWriter;

public class VirtualFilePointerImpl extends UserDataHolderBase implements VirtualFilePointer, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.VirtualFilePointerImpl");
  private String myUrl; // is null when myFile is not null
  private VirtualFile myFile;
  private boolean myWasRecentlyValid = false;
  private final VirtualFileManager myVirtualFileManager;
  private final VirtualFilePointerListener myListener;
  @NonNls private static final String ATTRIBUTE_URL = "url";
  private boolean disposed = false;
  private int useCount;

  private static final Key<Throwable> CREATE_TRACE = Key.create("CREATION_TRACE");
  private static final Key<Throwable> KILL_TRACE = Key.create("KILL_TRACE");

  VirtualFilePointerImpl(VirtualFile file, String url, VirtualFileManager virtualFileManager, VirtualFilePointerListener listener, Disposable parentDisposable) {
    myFile = file;
    myUrl = url;
    myVirtualFileManager = virtualFileManager;
    myListener = listener;
    useCount = 0;
    if (LOG.isDebugEnabled()) {
      putUserData(CREATE_TRACE, new Throwable("parent ="+parentDisposable));
    }
  }

  @NotNull
  public String getFileName() {
    update();

    if (myFile != null) {
      return myFile.getName();
    }
    else {
      int index = myUrl.lastIndexOf('/');
      return index >= 0 ? myUrl.substring(index + 1) : myUrl;
    }
  }

  public VirtualFile getFile() {
    checkDisposed();

    update();
    if (myFile != null && !myFile.isValid()) {
      myUrl = myFile.getUrl();
      myFile = null;
      update();
    }
    return myFile;
  }

  @NotNull
  public String getUrl() {
    //checkDisposed(); no check here since Disposer might want to compute hashcode during dispose()
    update();
    return myUrl == null ? myFile.getUrl() : myUrl;
  }

  @NotNull
  public String getPresentableUrl() {
    checkDisposed();
    update();

    return PathUtil.toPresentableUrl(getUrl());
  }

  private void checkDisposed() {
    if (disposed) throw new MyEx("Already disposed: "+toString(), getUserData(CREATE_TRACE), getUserData(KILL_TRACE));
  }

  public void throwNotDisposedError(String msg) throws RuntimeException {
    throw new RuntimeException(msg+"\n" +
                               "url=" + this +
                               "\nCreation trace:\n", getUserData(CREATE_TRACE));
  }

  public int incrementUsageCount() {
    return ++useCount;
  }

  private static class MyEx extends RuntimeException {
    private final Throwable e1;
    private final Throwable e2;

    private MyEx(String message, Throwable e1, Throwable e2) {
      super(message);
      this.e1 = e1;
      this.e2 = e2;
    }

    @Override
    public void printStackTrace(PrintStream s) {
      printStackTrace(new PrintWriter(s));
    }

    @Override
    public void printStackTrace(PrintWriter s) {
      super.printStackTrace(s);
      if (e1 != null) {
        s.println("--------------Creation trace: ");
        e1.printStackTrace(s);
      }
      if (e2 != null) {
        s.println("--------------Kill trace: ");
        e2.printStackTrace(s);
      }
    }
  }

  public boolean isValid() {
    update();

    return isFileRetrieved();
  }

  private boolean isFileRetrieved() {
    return !disposed && myFile != null; // && myFile.isValid();
  }

  void update() {
    if (!isFileRetrieved()) {
      LOG.assertTrue(myUrl != null);
      myFile = myVirtualFileManager.findFileByUrl(myUrl);
      if (myFile != null) {
        myUrl = null;
      }
    }
    else if (!myFile.exists()) {
      myUrl = myFile.getUrl();
      myFile = null;
    }

    myWasRecentlyValid = isFileRetrieved();
  }

  public void readExternal(Element element) throws InvalidDataException {
    myUrl = element.getAttributeValue(ATTRIBUTE_URL);
    myFile = null;
  }

  public void writeExternal(Element element) throws WriteExternalException {
    update();

    element.setAttribute(ATTRIBUTE_URL, getUrl());
  }

  boolean willValidityChange() {
    update();

    if (myWasRecentlyValid) {
      LOG.assertTrue(myFile != null);
      return !myFile.isValid();
    }
    else {
      LOG.assertTrue(myUrl != null);
      final VirtualFile fileByUrl = myVirtualFileManager.findFileByUrl(myUrl);
      return fileByUrl != null;
    }
  }

  @Override
  public String toString() {
    return myFile == null ? myUrl : myFile.getUrl();
  }

  public void dispose() {
    if (disposed) {
      throw new MyEx("Punching the dead horse.\nurl="+toString(), getUserData(CREATE_TRACE), getUserData(KILL_TRACE));
    }
    if (--useCount == 0) {
      if (LOG.isDebugEnabled()) {
        putUserData(KILL_TRACE, new Throwable());
      }
      String url = getUrl();
      disposed = true;
      ((VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance()).clearPointerCaches(url, myListener);
    }
  }
}
