package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *  @author dsl
 */
public class VirtualFilePointerContainerImpl implements VirtualFilePointerContainer, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer");
  private final List<VirtualFilePointer> myList = new ArrayList<VirtualFilePointer>();
  private final List<VirtualFilePointer> myReadOnlyList = Collections.unmodifiableList(myList);
  private final VirtualFilePointerManagerImpl myVirtualFilePointerManager;
  private final Disposable myParent;
  private VirtualFilePointerListener myListener;
  private VirtualFile[] myCachedDirectories;
  @NonNls private static final String URL_ATTR = "url";
  private boolean myDisposed;

  public VirtualFilePointerContainerImpl(VirtualFilePointerManagerImpl manager, Disposable parent, VirtualFilePointerListener listener) {
    myVirtualFilePointerManager = manager;
    myParent = parent;
    myListener = listener;
  }

  public void readExternal(final Element rootChild, final String childElements) throws InvalidDataException {
    final List urls = rootChild.getChildren(childElements);
    for (Object url : urls) {
      Element pathElement = (Element)url;
      final String urlAttribute = pathElement.getAttributeValue(URL_ATTR);
      if (urlAttribute == null) throw new InvalidDataException("path element without url");
      add(urlAttribute);
    }
  }

  public void writeExternal(final Element element, final String childElementName) {
    for (int i = 0; i < getList().size(); i++) {
      String url = getList().get(i).getUrl();
      final Element rootPathElement = new Element(childElementName);
      rootPathElement.setAttribute(URL_ATTR, url);
      element.addContent(rootPathElement);
    }
  }

  public void moveUp(String url) {
    int index = indexOf(url);
    if (index <= 0) return;
    dropCaches();
    ContainerUtil.swapElements(myList, index - 1, index);
  }

  public void moveDown(String url) {
    int index = indexOf(url);
    if (index < 0 || index + 1 >= myList.size()) return;
    dropCaches();
    ContainerUtil.swapElements(myList, index, index + 1);
  }

  private int indexOf(final String url) {
    for (int i = 0; i < myList.size(); i++) {
      final VirtualFilePointer pointer = myList.get(i);
      if (url.equals(pointer.getUrl())) {
        return i;
      }
    }

    return -1;
  }

  public void killAll() {
    myList.clear();
  }

  public void add(VirtualFile file) {
    assert !myDisposed;
    dropCaches();
    final VirtualFilePointer pointer = create(file);
    myList.add(pointer);
  }

  public void add(String url) {
    assert !myDisposed;
    dropCaches();
    final VirtualFilePointer pointer = create(url);
    myList.add(pointer);
  }

  public void remove(VirtualFilePointer pointer) {
    assert !myDisposed;
    dropCaches();
    final boolean result = myList.remove(pointer);
    LOG.assertTrue(result);
  }

  public List<VirtualFilePointer> getList() {
    assert !myDisposed;
    return myReadOnlyList;
  }

  public void addAll(VirtualFilePointerContainer that) {
    assert !myDisposed;
    dropCaches();

    List<VirtualFilePointer> thatList = ((VirtualFilePointerContainerImpl)that).myList;
    for (final VirtualFilePointer pointer : thatList) {
      myList.add(duplicate(pointer));
    }
  }

  void dropCaches() {
    myCachedDirectories = null;
    myCachedFiles = null;
    myCachedUrls = null;
  }

  private String[] myCachedUrls;
  public String[] getUrls() {
    assert !myDisposed;
    if (myCachedUrls == null) {
      myCachedUrls = calcUrls();
    }
    return myCachedUrls;
  }

  private String[] calcUrls() {
    if (myList.isEmpty()) return ArrayUtil.EMPTY_STRING_ARRAY;
    final ArrayList<String> result = new ArrayList<String>(myList.size());
    for (VirtualFilePointer smartVirtualFilePointer : myList) {
      result.add(smartVirtualFilePointer.getUrl());
    }
    return result.toArray(new String[result.size()]);
  }

  private VirtualFile[] myCachedFiles;
  public VirtualFile[] getFiles() {
    assert !myDisposed;
    if (myCachedFiles == null) {
      myCachedFiles = calcFiles();
    }
    return myCachedFiles;
  }

  private VirtualFile[] calcFiles() {
    if (myList.isEmpty()) return VirtualFile.EMPTY_ARRAY;
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>(myList.size());
    for (VirtualFilePointer pointer : myList) {
      final VirtualFile file = pointer.getFile();
      if (file != null) {
        result.add(file);
      }
    }
    return result.toArray(new VirtualFile[result.size()]);
  }

  public VirtualFile[] getDirectories() {
    assert !myDisposed;
    if (myCachedDirectories == null) {
      myCachedDirectories = calcDirectories();
    }
    return myCachedDirectories;
  }

  private VirtualFile[] calcDirectories() {
    if (myList.isEmpty()) return VirtualFile.EMPTY_ARRAY;
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>(myList.size());
    for (VirtualFilePointer smartVirtualFilePointer : myList) {
      final VirtualFile file = smartVirtualFilePointer.getFile();
      if (file != null && file.isDirectory()) {
        LOG.assertTrue(file.isValid());
        result.add(file);
      }
    }
    return result.isEmpty() ? VirtualFile.EMPTY_ARRAY : result.toArray(new VirtualFile[result.size()]);
  }

  @Nullable
  public VirtualFilePointer findByUrl(String url) {
    assert !myDisposed;
    for (VirtualFilePointer pointer : myList) {
      if (pointer.getUrl().equals(url)) return pointer;
    }
    return null;
  }

  public void clear() {
    dropCaches();
    killAll();
  }

  public int size() {
    return myList.size();
  }

  public Object get(int index) {
    return myList.get(index);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof VirtualFilePointerContainerImpl)) return false;

    final VirtualFilePointerContainerImpl virtualFilePointerContainer = (VirtualFilePointerContainerImpl)o;

    return !(myList != null ? !myList.equals(virtualFilePointerContainer.myList) : virtualFilePointerContainer.myList != null);
  }

  public int hashCode() {
    return myList != null ? myList.hashCode() : 0;
  }

  protected VirtualFilePointer create(VirtualFile file) {
    return myVirtualFilePointerManager.create(file, myParent, myListener);
  }

  protected VirtualFilePointer create(String url) {
    return myVirtualFilePointerManager.create(url, myParent, myListener);
  }

  protected VirtualFilePointer duplicate(VirtualFilePointer virtualFilePointer) {
    return myVirtualFilePointerManager.duplicate(virtualFilePointer, myParent, myListener);
  }

  @Override
  public String toString() {
    return "VFPContainer: "+myList/*+"; parent:"+myParent*/;
  }

  public VirtualFilePointerContainer clone(@NotNull Disposable parent) {
    return clone(parent, null);
  }

  public VirtualFilePointerContainer clone(@NotNull Disposable parent, VirtualFilePointerListener listener) {
    assert !myDisposed;
    VirtualFilePointerContainer clone = myVirtualFilePointerManager.createContainer(parent, listener);
    for (VirtualFilePointer pointer : myList) {
      clone.add(pointer.getUrl());
    }
    return clone;
  }

  public void dispose() {
    myDisposed = true;
  }
}
