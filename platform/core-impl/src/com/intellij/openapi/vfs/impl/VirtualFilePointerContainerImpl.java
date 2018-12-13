// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ConcurrentList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

/**
 * @author dsl
 */
public class VirtualFilePointerContainerImpl extends TraceableDisposable implements VirtualFilePointerContainer, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer");
  @NotNull private final ConcurrentList<VirtualFilePointer> myList = ContainerUtil.createConcurrentList();
  @NotNull private final ConcurrentList<VirtualFilePointer> myJarDirectories = ContainerUtil.createConcurrentList();
  @NotNull private final ConcurrentList<VirtualFilePointer> myJarRecursiveDirectories = ContainerUtil.createConcurrentList();
  @NotNull private final VirtualFilePointerManager myVirtualFilePointerManager;
  @NotNull private final Disposable myParent;
  private final VirtualFilePointerListener myListener;
  private volatile Trinity<String[], VirtualFile[], VirtualFile[]> myCachedThings;
  private volatile long myTimeStampOfCachedThings = -1;
  @NonNls public static final String URL_ATTR = "url";
  private boolean myDisposed;
  private static final boolean TRACE_CREATION = LOG.isDebugEnabled() || ApplicationManager.getApplication().isUnitTestMode();
  @NonNls public static final String JAR_DIRECTORY_ELEMENT = "jarDirectory";
  @NonNls public static final String RECURSIVE_ATTR = "recursive";

  VirtualFilePointerContainerImpl(@NotNull VirtualFilePointerManager manager,
                                  @NotNull Disposable parentDisposable,
                                  @Nullable VirtualFilePointerListener listener) {
    super(TRACE_CREATION && !ApplicationInfoImpl.isInStressTest());
    myVirtualFilePointerManager = manager;
    myParent = parentDisposable;
    myListener = listener;
  }

  @Override
  public void readExternal(@NotNull final Element rootChild, @NotNull final String childName, boolean externalizeJarDirectories) throws InvalidDataException {
    final List<Element> urls = rootChild.getChildren(childName);
    addAll(ContainerUtil.map(urls, url -> url.getAttributeValue(URL_ATTR)));
    if (externalizeJarDirectories) {
      List<Element> jarDirs = rootChild.getChildren(JAR_DIRECTORY_ELEMENT);
      for (Element jarDir : jarDirs) {
        String url = jarDir.getAttributeValue(URL_ATTR);
        if (url == null) throw new InvalidDataException("path element without url: " + JDOMUtil.getValue(jarDir));
        boolean recursive = Boolean.valueOf(jarDir.getAttributeValue(RECURSIVE_ATTR, "false"));
        addJarDirectory(url, recursive);
      }
    }
  }

  @Override
  public void writeExternal(@NotNull final Element element, @NotNull final String childElementName, boolean externalizeJarDirectories) {
    for (VirtualFilePointer pointer : myList) {
      String url = pointer.getUrl();
      final Element rootPathElement = new Element(childElementName);
      rootPathElement.setAttribute(URL_ATTR, url);
      element.addContent(rootPathElement);
    }
    if (externalizeJarDirectories) {
      writeJarDirs(myJarDirectories, element, false);
      writeJarDirs(myJarRecursiveDirectories, element, true);
    }
  }

  private static void writeJarDirs(@NotNull List<VirtualFilePointer> myJarDirectories,
                                   @NotNull Element element,
                                   boolean recursive) {
    List<VirtualFilePointer> jarDirectories = new ArrayList<>(myJarDirectories);
    Collections.sort(jarDirectories, Comparator.comparing(VirtualFilePointer::getUrl, String.CASE_INSENSITIVE_ORDER));
    for (VirtualFilePointer pointer : jarDirectories) {
      String url = pointer.getUrl();
      final Element jarDirElement = new Element(JAR_DIRECTORY_ELEMENT);
      jarDirElement.setAttribute(URL_ATTR, url);
      jarDirElement.setAttribute(RECURSIVE_ATTR, Boolean.toString(recursive));
      element.addContent(jarDirElement);
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
    myList.addIfAbsent(create(file));
  }

  @Override
  public void add(@NotNull String url) {
    assert !myDisposed;
    dropCaches();
    myList.addIfAbsent(create(url));
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

    addAll(Arrays.asList(that.getUrls()));

    List<VirtualFilePointer> jarDups = ContainerUtil.map(((VirtualFilePointerContainerImpl)that).myJarDirectories, this::duplicate);
    List<VirtualFilePointer> jarRecursiveDups = ContainerUtil.map(((VirtualFilePointerContainerImpl)that).myJarRecursiveDirectories, this::duplicate);
    myJarDirectories.addAllAbsent(jarDups);
    myJarRecursiveDirectories.addAllAbsent(jarRecursiveDups);
  }

  public void addAll(@NotNull Collection<String> urls) {
    // optimization: faster than calling .add() one by one
    myList.addAllAbsent(ContainerUtil.map(urls, url->create(url)));
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
    if (myList.isEmpty() && myJarDirectories.isEmpty() && myJarRecursiveDirectories.isEmpty()) {
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
      for (VirtualFilePointer jarDirectoryPtr : myJarDirectories) {
        VirtualFile jarDirectory = jarDirectoryPtr.getFile();
        if (jarDirectory != null) {
          // getFiles() must return files under jar directories but must not return jarDirectories themselves
          cachedDirectories.remove(jarDirectory);

          VirtualFile[] children = jarDirectory.getChildren();
          for (VirtualFile file : children) {
            if (!file.isDirectory() && FileTypeRegistry.getInstance().getFileTypeByFileName(file.getNameSequence()) == ArchiveFileType.INSTANCE) {
              VirtualFile jarRoot = StandardFileSystems.jar().findFileByPath(file.getPath() + URLUtil.JAR_SEPARATOR);
              if (jarRoot != null) {
                cachedFiles.add(jarRoot);
                cachedDirectories.add(jarRoot);
              }
            }
          }
        }
      }
      for (VirtualFilePointer jarDirectoryPtr : myJarRecursiveDirectories) {
        VirtualFile jarDirectory = jarDirectoryPtr.getFile();
        if (jarDirectory != null) {
          // getFiles() must return files under jar directories but must not return jarDirectories themselves
          cachedDirectories.remove(jarDirectory);

          VfsUtilCore.visitChildrenRecursively(jarDirectory, new VirtualFileVisitor() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
              if (!file.isDirectory() && FileTypeRegistry.getInstance().getFileTypeByFileName(file.getNameSequence()) == ArchiveFileType.INSTANCE) {
                VirtualFile jarRoot = StandardFileSystems.jar().findFileByPath(file.getPath() + URLUtil.JAR_SEPARATOR);
                if (jarRoot != null) {
                  cachedFiles.add(jarRoot);
                  cachedDirectories.add(jarRoot);
                  return false;
                }
              }
              return true;
            }
          });
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
    for (VirtualFilePointer pointer : ContainerUtil.concat(myList, myJarDirectories, myJarRecursiveDirectories)) {
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
    return myList.size()+myJarDirectories.size()+myJarRecursiveDirectories.size();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof VirtualFilePointerContainerImpl)) return false;

    VirtualFilePointerContainerImpl impl = (VirtualFilePointerContainerImpl)o;

    return myList.equals(impl.myList) &&
           myJarDirectories.equals(impl.myJarDirectories) &&
           myJarRecursiveDirectories.equals(impl.myJarRecursiveDirectories);
  }

  @Override
  public int hashCode() {
    return myList.hashCode();
  }

  @NotNull
  protected VirtualFilePointer create(@NotNull VirtualFile file) {
    return myVirtualFilePointerManager.create(file, myParent, myListener);
  }

  @NotNull
  protected VirtualFilePointer create(@NotNull String url) {
    return myVirtualFilePointerManager.create(url, myParent, myListener);
  }

  @NotNull
  protected VirtualFilePointer duplicate(@NotNull VirtualFilePointer virtualFilePointer) {
    return myVirtualFilePointerManager.duplicate(virtualFilePointer, myParent, myListener);
  }

  @NotNull
  @NonNls
  @Override
  public String toString() {
    return "VFPContainer: " + myList
           + (myJarDirectories.isEmpty() ? "" : ", jars: "+myJarDirectories)
           + (myJarRecursiveDirectories.isEmpty() ? "" : ", jars(recursively): "+myJarRecursiveDirectories);
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
    VirtualFilePointerContainerImpl clone = (VirtualFilePointerContainerImpl)myVirtualFilePointerManager.createContainer(parent, listener);

    List<VirtualFilePointer> toAdd = ContainerUtil.map(myList, p -> clone.create(p.getUrl()));
    clone.myList.addAll(toAdd);
    clone.addAllJarDirectories(ContainerUtil.map(myJarDirectories, VirtualFilePointer::getUrl), false);
    clone.addAllJarDirectories(ContainerUtil.map(myJarRecursiveDirectories, VirtualFilePointer::getUrl), true);
    return clone;
  }

  @Override
  public void dispose() {
    assert !myDisposed;
    myDisposed = true;
    kill(null);
    clear();
  }

  @Override
  public void addJarDirectory(@NotNull String directoryUrl, boolean recursively) {
    VirtualFilePointer pointer = myVirtualFilePointerManager.createDirectoryPointer(directoryUrl, recursively, myParent, myListener);
    (recursively ? myJarRecursiveDirectories : myJarDirectories).addIfAbsent(pointer);

    myList.addIfAbsent(pointer); // hack. jar directories need to be contained in class roots too (for externalization compatibility) but be ignored in getFiles()
    dropCaches();
  }

  /** optimization: faster than calling {@link #addJarDirectory(String, boolean)} one by one */
  public void addAllJarDirectories(@NotNull Collection<String> directoryUrls, boolean recursively) {
    if (directoryUrls.isEmpty()) return;
    List<VirtualFilePointer> pointers = ContainerUtil.map(directoryUrls, url -> myVirtualFilePointerManager.createDirectoryPointer(url, recursively, myParent, myListener));
    (recursively ? myJarRecursiveDirectories : myJarDirectories).addAllAbsent(pointers);
    myList.addAllAbsent(pointers); // hack. jar directories need to be contained in class roots too (for externalization compatibility) but be ignored in getFiles()
    dropCaches();
  }

  @Override
  public boolean removeJarDirectory(@NotNull String directoryUrl) {
    dropCaches();
    Predicate<VirtualFilePointer> filter = ptr -> FileUtil.pathsEqual(ptr.getUrl(), directoryUrl);
    boolean removed0 = myList.removeIf(filter);
    boolean removed1 = myJarDirectories.removeIf(filter);
    boolean removed2 = myJarRecursiveDirectories.removeIf(filter);
    return removed0 || removed1 || removed2;
  }

  @NotNull
  @Override
  public List<Pair<String, Boolean>> getJarDirectories() {
    List<Pair<String, Boolean>> jars = ContainerUtil.map(myJarDirectories, ptr -> Pair.create(ptr.getUrl(), false));
    List<Pair<String, Boolean>> recJars = ContainerUtil.map(myJarRecursiveDirectories, ptr -> Pair.create(ptr.getUrl(), true));
    return ContainerUtil.concat(jars, recJars);
  }
}
