/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.TraceableDisposable;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author dsl
 */
class VirtualFilePointerContainerImpl extends TraceableDisposable implements VirtualFilePointerContainer, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer");
  @NotNull private final List<VirtualFilePointer> myList = ContainerUtil.createLockFreeCopyOnWriteList();
  @NotNull private final VirtualFilePointerManager myVirtualFilePointerManager;
  @NotNull private final Disposable myParent;
  private final VirtualFilePointerListener myListener;
  private volatile Trinity<String[], VirtualFile[], VirtualFile[]> myCachedThings;
  private volatile long myTimeStampOfCachedThings = -1;
  @NonNls private static final String URL_ATTR = "url";
  private boolean myDisposed;
  private static final boolean TRACE_CREATION = LOG.isDebugEnabled() || ApplicationManager.getApplication().isUnitTestMode();
  VirtualFilePointerContainerImpl(@NotNull VirtualFilePointerManager manager,
                                  @NotNull Disposable parentDisposable,
                                  @Nullable VirtualFilePointerListener listener) {
    //noinspection HardCodedStringLiteral
    super(TRACE_CREATION && !ApplicationInfoImpl.isInStressTest());
    myVirtualFilePointerManager = manager;
    myParent = parentDisposable;
    myListener = listener;
  }

  @Override
  public void readExternal(@NotNull final Element rootChild, @NotNull final String childElements) throws InvalidDataException {
    final List<Element> urls = rootChild.getChildren(childElements);
    for (Element url : urls) {
      final String urlAttribute = url.getAttributeValue(URL_ATTR);
      if (urlAttribute == null) throw new InvalidDataException("path element without url");
      add(urlAttribute);
    }
  }

  @Override
  public void writeExternal(@NotNull final Element element, @NotNull final String childElementName) {
    for (VirtualFilePointer pointer : myList) {
      String url = pointer.getUrl();
      final Element rootPathElement = new Element(childElementName);
      rootPathElement.setAttribute(URL_ATTR, url);
      element.addContent(rootPathElement);
    }
  }

  @Override
  public void moveUp(@NotNull String url) {
    int index = indexOf(url);
    if (index <= 0) return;
    dropCaches();
    ContainerUtil.swapElements(myList, index - 1, index);
  }

  @Override
  public void moveDown(@NotNull String url) {
    int index = indexOf(url);
    if (index < 0 || index + 1 >= myList.size()) return;
    dropCaches();
    ContainerUtil.swapElements(myList, index, index + 1);
  }

  private int indexOf(@NotNull final String url) {
    for (int i = 0; i < myList.size(); i++) {
      final VirtualFilePointer pointer = myList.get(i);
      if (url.equals(pointer.getUrl())) {
        return i;
      }
    }

    return -1;
  }

  @Override
  public void killAll() {
    myList.clear();
  }

  @Override
  public void add(@NotNull VirtualFile file) {
    assert !myDisposed;
    dropCaches();
    final VirtualFilePointer pointer = create(file);
    myList.add(pointer);
  }

  @Override
  public void add(@NotNull String url) {
    assert !myDisposed;
    dropCaches();
    final VirtualFilePointer pointer = create(url);
    myList.add(pointer);
  }

  @Override
  public void remove(@NotNull VirtualFilePointer pointer) {
    assert !myDisposed;
    dropCaches();
    final boolean result = myList.remove(pointer);
    LOG.assertTrue(result);
  }

  @Override
  @NotNull
  public List<VirtualFilePointer> getList() {
    assert !myDisposed;
    return Collections.unmodifiableList(myList);
  }

  @Override
  public void addAll(@NotNull VirtualFilePointerContainer that) {
    assert !myDisposed;
    dropCaches();

    for (final VirtualFilePointer pointer : that.getList()) {
      myList.add(duplicate(pointer));
    }
  }

  private void dropCaches() {
    myTimeStampOfCachedThings = -1; // make it never equal to myVirtualFilePointerManager.getModificationCount()
    myCachedThings = EMPTY;
  }

  @Override
  @NotNull
  public String[] getUrls() {
    return getOrCache().first;
  }

  @NotNull
  private Trinity<String[], VirtualFile[], VirtualFile[]> getOrCache() {
    assert !myDisposed;
    long timeStamp = myTimeStampOfCachedThings;
    Trinity<String[], VirtualFile[], VirtualFile[]> cached = myCachedThings;
    return timeStamp == myVirtualFilePointerManager.getModificationCount() ? cached : cacheThings();
  }

  private static final Trinity<String[], VirtualFile[], VirtualFile[]> EMPTY =
    Trinity.create(ArrayUtil.EMPTY_STRING_ARRAY, VirtualFile.EMPTY_ARRAY, VirtualFile.EMPTY_ARRAY);

  @NotNull
  private Trinity<String[], VirtualFile[], VirtualFile[]> cacheThings() {
    Trinity<String[], VirtualFile[], VirtualFile[]> result;
    if (myList.isEmpty()) {
      result = EMPTY;
    }
    else {
      List<VirtualFile> cachedFiles = new ArrayList<>(myList.size());
      List<String> cachedUrls = new ArrayList<>(myList.size());
      List<VirtualFile> cachedDirectories = new ArrayList<>(myList.size() / 3);
      boolean allFilesAreDirs = true;
      for (VirtualFilePointer v : myList) {
        VirtualFile file = v.getFile();
        String url = v.getUrl();
        cachedUrls.add(url);
        if (file != null) {
          cachedFiles.add(file);
          if (file.isDirectory()) {
            cachedDirectories.add(file);
          }
          else {
            allFilesAreDirs = false;
          }
        }
      }
      VirtualFile[] directories = VfsUtilCore.toVirtualFileArray(cachedDirectories);
      VirtualFile[] files = allFilesAreDirs ? directories : VfsUtilCore.toVirtualFileArray(cachedFiles);
      String[] urlsArray = ArrayUtil.toStringArray(cachedUrls);
      result = Trinity.create(urlsArray, files, directories);
    }
    myCachedThings = result;
    myTimeStampOfCachedThings = myVirtualFilePointerManager.getModificationCount();
    return result;
  }

  @Override
  @NotNull
  public VirtualFile[] getFiles() {
    return getOrCache().second;
  }

  @Override
  @NotNull
  public VirtualFile[] getDirectories() {
    return getOrCache().third;
  }

  @Override
  @Nullable
  public VirtualFilePointer findByUrl(@NotNull String url) {
    assert !myDisposed;
    for (VirtualFilePointer pointer : myList) {
      if (url.equals(pointer.getUrl())) return pointer;
    }
    return null;
  }

  @Override
  public void clear() {
    dropCaches();
    killAll();
  }

  @Override
  public int size() {
    return myList.size();
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof VirtualFilePointerContainerImpl)) return false;

    final VirtualFilePointerContainerImpl virtualFilePointerContainer = (VirtualFilePointerContainerImpl)o;

    return myList.equals(virtualFilePointerContainer.myList);
  }

  public int hashCode() {
    return myList.hashCode();
  }

  protected VirtualFilePointer create(@NotNull VirtualFile file) {
    return myVirtualFilePointerManager.create(file, myParent, myListener);
  }

  protected VirtualFilePointer create(@NotNull String url) {
    return myVirtualFilePointerManager.create(url, myParent, myListener);
  }

  protected VirtualFilePointer duplicate(@NotNull VirtualFilePointer virtualFilePointer) {
    return myVirtualFilePointerManager.duplicate(virtualFilePointer, myParent, myListener);
  }

  @NotNull
  @NonNls
  @Override
  public String toString() {
    return "VFPContainer: " + myList/*+"; parent:"+myParent*/;
  }

  @Override
  @NotNull
  public VirtualFilePointerContainer clone(@NotNull Disposable parent) {
    return clone(parent, null);
  }

  @Override
  @NotNull
  public VirtualFilePointerContainer clone(@NotNull Disposable parent, @Nullable VirtualFilePointerListener listener) {
    assert !myDisposed;
    VirtualFilePointerContainer clone = myVirtualFilePointerManager.createContainer(parent, listener);
    for (VirtualFilePointer pointer : myList) {
      clone.add(pointer.getUrl());
    }
    return clone;
  }

  @Override
  public void dispose() {
    assert !myDisposed;
    myDisposed = true;
    kill(null);
  }
}
