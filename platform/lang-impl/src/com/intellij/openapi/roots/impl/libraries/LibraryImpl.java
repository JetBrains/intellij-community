/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.roots.impl.RootModelImpl;
import com.intellij.openapi.roots.impl.RootProviderBaseImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author dsl
 */
public class LibraryImpl implements LibraryEx.ModifiableModelEx, LibraryEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.impl.LibraryImpl");
  @NonNls public static final String LIBRARY_NAME_ATTR = "name";
  @NonNls private static final String ROOT_PATH_ELEMENT = "root";
  @NonNls public static final String ELEMENT = "library";
  @NonNls private static final String JAR_DIRECTORY_ELEMENT = "jarDirectory";
  @NonNls private static final String URL_ATTR = "url";
  @NonNls private static final String RECURSIVE_ATTR = "recursive";
  private String myName;
  private final LibraryTable myLibraryTable;
  private final Map<OrderRootType, VirtualFilePointerContainer> myRoots;
  private final Map<String, Boolean> myJarDirectories = new HashMap<String, Boolean>();
  private final List<LocalFileSystem.WatchRequest> myWatchRequests = new ArrayList<LocalFileSystem.WatchRequest>();
  private final LibraryImpl mySource;

  private final MyRootProviderImpl myRootProvider = new MyRootProviderImpl();
  private final ModifiableRootModel myRootModel;
  private MessageBusConnection myBusConnection = null;
  private boolean myDisposed;
  private final Disposable myPointersDisposable = Disposer.newDisposable();

  LibraryImpl(LibraryTable table, Element element, ModifiableRootModel rootModel) throws InvalidDataException {
    myLibraryTable = table;
    myRootModel = rootModel;
    mySource = null;
    readName(element);
    readJarDirectories(element);
    //init roots depends on my hashcode, hashcode depends on jardirectories and name
    myRoots = initRoots();
    readRoots(element);
    updateWatchedRoots();
  }

  LibraryImpl(String name, LibraryTable table, ModifiableRootModel rootModel) {
    myName = name;
    myLibraryTable = table;
    myRootModel = rootModel;
    myRoots = initRoots();
    mySource = null;
  }

  private LibraryImpl(LibraryImpl from, LibraryImpl newSource, ModifiableRootModel rootModel) {
    assert !from.isDisposed();
    myRootModel = rootModel;
    myName = from.myName;
    myRoots = initRoots();
    mySource = newSource;
    myLibraryTable = from.myLibraryTable;
    for (OrderRootType rootType : OrderRootType.getAllTypes()) {
      final VirtualFilePointerContainer thisContainer = myRoots.get(rootType);
      final VirtualFilePointerContainer thatContainer = from.myRoots.get(rootType);
      thisContainer.addAll(thatContainer);
    }
    myJarDirectories.putAll(from.myJarDirectories);
  }

  public void dispose() {
    assert !isDisposed();
    if (!myWatchRequests.isEmpty()) {
      LocalFileSystem.getInstance().removeWatchedRoots(myWatchRequests);
      myWatchRequests.clear();
    }
    if (myBusConnection != null) {
      myBusConnection.disconnect();
      myBusConnection = null;
    }
    myDisposed = true;
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  public String getName() {
    return myName;
  }

  @NotNull
  public String[] getUrls(@NotNull OrderRootType rootType) {
    assert !isDisposed();
    final VirtualFilePointerContainer result = myRoots.get(rootType);
    return result.getUrls();
  }

  @NotNull
  public VirtualFile[] getFiles(@NotNull OrderRootType rootType) {
    assert !isDisposed();
    final List<VirtualFile> expanded = new ArrayList<VirtualFile>();
    for (VirtualFile file : myRoots.get(rootType).getFiles()) {
      if (file.isDirectory()) {
        final Boolean expandRecursively = myJarDirectories.get(file.getUrl());
        if (expandRecursively != null) {
          addChildren(file, expanded, expandRecursively.booleanValue());
          continue;
        }
      }
      expanded.add(file);
    }
    return VfsUtil.toVirtualFileArray(expanded);
  }

  private static void addChildren(final VirtualFile dir, final List<VirtualFile> container, final boolean recursively) {
    for (VirtualFile child : dir.getChildren()) {
      final FileType fileType = child.getFileType();
      if (FileTypes.ARCHIVE.equals(fileType)) {
        final StringBuilder builder = StringBuilderSpinAllocator.alloc();
        try {
          builder.append(VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, child.getPath()));
          builder.append(JarFileSystem.JAR_SEPARATOR);
          final VirtualFile jarRoot = VirtualFileManager.getInstance().findFileByUrl(builder.toString());
          if (jarRoot != null) {
            container.add(jarRoot);
          }
        }
        finally {
          StringBuilderSpinAllocator.dispose(builder);
        }
      }
      else {
        if (recursively && child.isDirectory()) {
          addChildren(child, container, recursively);
        }
      }
    }
  }

  public void setName(String name) {
    LOG.assertTrue(isWritable());
    myName = name;
  }

  /* you have to commit modifiable model or dispose it by yourself! */
  @NotNull
  public ModifiableModel getModifiableModel() {
    assert !isDisposed();
    return new LibraryImpl(this, this, myRootModel);
  }

  public Library cloneLibrary(RootModelImpl rootModel) {
    LOG.assertTrue(myLibraryTable == null);
    final LibraryImpl clone = new LibraryImpl(this, null, rootModel);
    clone.updateWatchedRoots();
    return clone;
  }

  public boolean allPathsValid(OrderRootType type) {
    final List<VirtualFilePointer> pointers = myRoots.get(type).getList();
    for (VirtualFilePointer pointer : pointers) {
      if (!pointer.isValid()) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  public RootProvider getRootProvider() {
    return myRootProvider;
  }

  private Map<OrderRootType, VirtualFilePointerContainer> initRoots() {
    Disposer.register(this, myPointersDisposable);
    
    Map<OrderRootType, VirtualFilePointerContainer> result = new HashMap<OrderRootType, VirtualFilePointerContainer>(5);

    for (OrderRootType rootType : OrderRootType.getAllTypes()) {
      result.put(rootType, VirtualFilePointerManager.getInstance().createContainer(myPointersDisposable));
    }
    result.put(OrderRootType.COMPILATION_CLASSES, result.get(OrderRootType.CLASSES));
    result.put(OrderRootType.PRODUCTION_COMPILATION_CLASSES, result.get(OrderRootType.CLASSES));
    result.put(OrderRootType.CLASSES_AND_OUTPUT, result.get(OrderRootType.CLASSES));

    return result;
  }

  public void readExternal(Element element) throws InvalidDataException {
    readName(element);
    readRoots(element);
    readJarDirectories(element);
    updateWatchedRoots();
  }

  private void readName(Element element) {
    myName = element.getAttributeValue(LIBRARY_NAME_ATTR);
  }

  private void readRoots(Element element) throws InvalidDataException {
    for (OrderRootType rootType : OrderRootType.getAllTypes()) {
      final Element rootChild = element.getChild(rootType.name());
      if (rootChild == null) {
        continue;
      }
      VirtualFilePointerContainer roots = myRoots.get(rootType);
      roots.readExternal(rootChild, ROOT_PATH_ELEMENT);
    }
  }

  private void readJarDirectories(Element element) {
    myJarDirectories.clear();
    final List jarDirs = element.getChildren(JAR_DIRECTORY_ELEMENT);
    for (Object item : jarDirs) {
      final Element jarDir = (Element)item;
      final String url = jarDir.getAttributeValue(URL_ATTR);
      final String recursive = jarDir.getAttributeValue(RECURSIVE_ATTR);
      if (url != null) {
        myJarDirectories.put(url, Boolean.valueOf(Boolean.parseBoolean(recursive)));
      }
    }
  }


  public void writeExternal(Element rootElement) {
    LOG.assertTrue(!isDisposed(), "Already disposed!");

    Element element = new Element(ELEMENT);
    if (myName != null) {
      element.setAttribute(LIBRARY_NAME_ATTR, myName);
    }
    for (OrderRootType rootType : OrderRootType.getSortedRootTypes()) {
      final VirtualFilePointerContainer roots = myRoots.get(rootType);
      if (roots.size() == 0 && rootType.skipWriteIfEmpty()) continue; //compatibility iml/ipr
      final Element rootTypeElement = new Element(rootType.name());
      roots.writeExternal(rootTypeElement, ROOT_PATH_ELEMENT);
      if (rootTypeElement.getAttributes().size() > 0 || rootTypeElement.getContent().size() > 0) element.addContent(rootTypeElement);
    }
    List<String> urls = new ArrayList<String>(myJarDirectories.keySet());
    Collections.sort(urls, new Comparator<String>() {
      public int compare(final String url1, final String url2) {
        return url1.compareToIgnoreCase(url2);
      }
    });
    for (String url : urls) {
      final Element jarDirElement = new Element(JAR_DIRECTORY_ELEMENT);
      jarDirElement.setAttribute(URL_ATTR, url);
      jarDirElement.setAttribute(RECURSIVE_ATTR, myJarDirectories.get(url).toString());
      element.addContent(jarDirElement);
    }
    rootElement.addContent(element);
  }

  private boolean isWritable() {
    return mySource != null;
  }

  public void addRoot(@NotNull String url, @NotNull OrderRootType rootType) {
    LOG.assertTrue(isWritable());
    assert !isDisposed();

    final VirtualFilePointerContainer container = myRoots.get(rootType);
    container.add(url);
  }

  public void addRoot(@NotNull VirtualFile file, @NotNull OrderRootType rootType) {
    LOG.assertTrue(isWritable());
    assert !isDisposed();

    final VirtualFilePointerContainer container = myRoots.get(rootType);
    container.add(file);
  }

  public void addJarDirectory(@NotNull final String url, final boolean recursive) {
    assert !isDisposed();
    LOG.assertTrue(isWritable());
    final VirtualFilePointerContainer container = myRoots.get(OrderRootType.CLASSES);
    container.add(url);
    myJarDirectories.put(url, Boolean.valueOf(recursive));
  }

  public void addJarDirectory(@NotNull final VirtualFile file, final boolean recursive) {
    assert !isDisposed();
    LOG.assertTrue(isWritable());
    final VirtualFilePointerContainer container = myRoots.get(OrderRootType.CLASSES);
    container.add(file);
    myJarDirectories.put(file.getUrl(), Boolean.valueOf(recursive));
  }

  public boolean isJarDirectory(@NotNull final String url) {
    return myJarDirectories.containsKey(url);
  }

  public boolean isValid(@NotNull final String url, @NotNull final OrderRootType rootType) {
    final VirtualFilePointerContainer container = myRoots.get(rootType);
    final VirtualFilePointer fp = container.findByUrl(url);
    return fp != null && fp.isValid();
  }

  public boolean removeRoot(@NotNull String url, @NotNull OrderRootType rootType) {
    assert !isDisposed();
    LOG.assertTrue(isWritable());
    final VirtualFilePointerContainer container = myRoots.get(rootType);
    final VirtualFilePointer byUrl = container.findByUrl(url);
    if (byUrl != null) {
      container.remove(byUrl);
      myJarDirectories.remove(url);
      return true;
    }
    return false;
  }

  public void moveRootUp(@NotNull String url, @NotNull OrderRootType rootType) {
    assert !isDisposed();
    LOG.assertTrue(isWritable());
    final VirtualFilePointerContainer container = myRoots.get(rootType);
    container.moveUp(url);
  }

  public void moveRootDown(@NotNull String url, @NotNull OrderRootType rootType) {
    assert !isDisposed();
    LOG.assertTrue(isWritable());
    final VirtualFilePointerContainer container = myRoots.get(rootType);
    container.moveDown(url);
  }

  public boolean isChanged() {
    return !mySource.equals(this);
  }

  private boolean areRootsChanged(final LibraryImpl that) {
    return !that.equals(this);
    //final OrderRootType[] allTypes = OrderRootType.getAllTypes();
    //for (OrderRootType type : allTypes) {
    //  final String[] urls = getUrls(type);
    //  final String[] thatUrls = that.getUrls(type);
    //  if (urls.length != thatUrls.length) {
    //    return true;
    //  }
    //  for (int idx = 0; idx < urls.length; idx++) {
    //    final String url = urls[idx];
    //    final String thatUrl = thatUrls[idx];
    //    if (!Comparing.equal(url, thatUrl)) {
    //      return true;
    //    }
    //    final Boolean jarDirRecursive = myJarDirectories.get(url);
    //    final Boolean sourceJarDirRecursive = that.myJarDirectories.get(thatUrl);
    //    if (jarDirRecursive == null ? sourceJarDirRecursive != null : !jarDirRecursive.equals(sourceJarDirRecursive)) {
    //      return true;
    //    }
    //  }
    //}
    //return false;
  }

  public Library getSource() {
    return mySource;
  }

  public void commit() {
    assert !isDisposed();
    mySource.commit(this);
    Disposer.dispose(this);
  }

  private void commit(@NotNull LibraryImpl fromModel) {
    if (myLibraryTable != null) {
      ApplicationManager.getApplication().assertWriteAccessAllowed();
    }
    if (!Comparing.equal(fromModel.myName, myName)) {
      myName = fromModel.myName;
      if (myLibraryTable instanceof LibraryTableBase) {
        ((LibraryTableBase)myLibraryTable).fireLibraryRenamed(this);
      }
    }
    if (areRootsChanged(fromModel)) {
      disposeMyPointers();
      copyRootsFrom(fromModel);
      myJarDirectories.clear();
      myJarDirectories.putAll(fromModel.myJarDirectories);
      updateWatchedRoots();
      myRootProvider.fireRootSetChanged();
    }
  }

  private void copyRootsFrom(LibraryImpl fromModel) {
    myRoots.clear();
    for (Map.Entry<OrderRootType, VirtualFilePointerContainer> entry : fromModel.myRoots.entrySet()) {
      OrderRootType rootType = entry.getKey();
      VirtualFilePointerContainer container = entry.getValue();
      VirtualFilePointerContainer clone = container.clone(myPointersDisposable);
      myRoots.put(rootType, clone);
    }
  }

  private void disposeMyPointers() {
    for (VirtualFilePointerContainer container : new THashSet<VirtualFilePointerContainer>(myRoots.values())) {
      container.killAll();
    }
    Disposer.dispose(myPointersDisposable);
    Disposer.register(this, myPointersDisposable);
  }

  private void updateWatchedRoots() {
    final LocalFileSystem fs = LocalFileSystem.getInstance();
    if (!myWatchRequests.isEmpty()) {
      fs.removeWatchedRoots(myWatchRequests);
      myWatchRequests.clear();
    }
    if (!myJarDirectories.isEmpty()) {
      final VirtualFileManager fm = VirtualFileManager.getInstance();
      for (Map.Entry<String, Boolean> entry : myJarDirectories.entrySet()) {
        String url = entry.getKey();
        if (fm.getFileSystem(VirtualFileManager.extractProtocol(url)) instanceof LocalFileSystem) {
          final boolean watchRecursively = entry.getValue().booleanValue();
          final LocalFileSystem.WatchRequest request = fs.addRootToWatch(VirtualFileManager.extractPath(url), watchRecursively);
          myWatchRequests.add(request);
        }
      }
      if (myBusConnection == null) {
        myBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
        myBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
          public void before(final List<? extends VFileEvent> events) {
          }

          public void after(final List<? extends VFileEvent> events) {
            boolean changesDetected = false;
            for (VFileEvent event : events) {
              if (event instanceof VFileCopyEvent) {
                final VFileCopyEvent copyEvent = (VFileCopyEvent)event;
                if (isUnderJarDirectory(copyEvent.getNewParent() + "/" + copyEvent.getNewChildName()) ||
                    isUnderJarDirectory(copyEvent.getFile().getUrl())) {
                  changesDetected = true;
                  break;
                }
              }
              else if (event instanceof VFileMoveEvent) {
                final VFileMoveEvent moveEvent = (VFileMoveEvent)event;

                final VirtualFile file = moveEvent.getFile();
                if (isUnderJarDirectory(file.getUrl()) || isUnderJarDirectory(moveEvent.getOldParent().getUrl() + "/" + file.getName())) {
                  changesDetected = true;
                  break;
                }
              }
              else if (event instanceof VFileDeleteEvent) {
                final VFileDeleteEvent deleteEvent = (VFileDeleteEvent)event;
                if (isUnderJarDirectory(deleteEvent.getFile().getUrl())) {
                  changesDetected = true;
                  break;
                }
              }
              else if (event instanceof VFileCreateEvent) {
                final VFileCreateEvent createEvent = (VFileCreateEvent)event;
                if (isUnderJarDirectory(createEvent.getParent().getUrl() + "/" + createEvent.getChildName())) {
                  changesDetected = true;
                  break;
                }
              }
            }

            if (changesDetected) {
              myRootProvider.fireRootSetChanged();
            }
          }

          private boolean isUnderJarDirectory(String url) {
            for (String rootUrl : myJarDirectories.keySet()) {
              if (FileUtil.startsWith(url, rootUrl)) {
                return true;
              }
            }
            return false;
          }
        });
      }
    }
    else {
      final MessageBusConnection connection = myBusConnection;
      if (connection != null) {
        myBusConnection = null;
        connection.disconnect();
      }
    }
  }

  private class MyRootProviderImpl extends RootProviderBaseImpl {
    @NotNull
    public String[] getUrls(@NotNull OrderRootType rootType) {
      Set<String> originalUrls = new LinkedHashSet<String>(Arrays.asList(LibraryImpl.this.getUrls(rootType)));
      for (VirtualFile file : getFiles(rootType)) { // Add those expanded with jar directories.
        originalUrls.add(file.getUrl());
      }
      return ArrayUtil.toStringArray(originalUrls);
    }

    @NotNull
    public VirtualFile[] getFiles(@NotNull final OrderRootType rootType) {
      return LibraryImpl.this.getFiles(rootType);
    }

    public void fireRootSetChanged() {
      super.fireRootSetChanged();
    }
  }

  public LibraryTable getTable() {
    return myLibraryTable;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final LibraryImpl library = (LibraryImpl)o;

    if (!myJarDirectories.equals(library.myJarDirectories)) return false;
    if (myName != null ? !myName.equals(library.myName) : library.myName != null) return false;
    if (myRoots != null ? !myRoots.equals(library.myRoots) : library.myRoots != null) return false;

    return true;
  }

  public int hashCode() {
    int result = myName != null ? myName.hashCode() : 0;
    result = 31 * result + (myRoots != null ? myRoots.hashCode() : 0);
    result = 31 * result + (myJarDirectories != null ? myJarDirectories.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "Library: name:" + myName + "; jars:" + myJarDirectories.keySet() + "; roots:" + myRoots.values();
  }

  @Nullable("will return non-null value only for module level libraries")
  public Module getModule() {
    return myRootModel == null ? null : myRootModel.getModule();
  }
}
