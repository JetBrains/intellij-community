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
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.PersistentOrderRootType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.roots.impl.RootModelImpl;
import com.intellij.openapi.roots.impl.RootProviderBaseImpl;
import com.intellij.openapi.roots.libraries.*;
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
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
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
  @NonNls private static final String LIBRARY_TYPE_ATTR = "type";
  @NonNls private static final String ROOT_PATH_ELEMENT = "root";
  @NonNls public static final String ELEMENT = "library";
  @NonNls private static final String JAR_DIRECTORY_ELEMENT = "jarDirectory";
  @NonNls private static final String URL_ATTR = "url";
  @NonNls private static final String RECURSIVE_ATTR = "recursive";
  @NonNls private static final String ROOT_TYPE_ATTR = "type";
  @NonNls private static final String PROPERTIES_ELEMENT = "properties";
  private static final OrderRootType DEFAULT_JAR_DIRECTORY_TYPE = OrderRootType.CLASSES;
  private static final SkipDefaultValuesSerializationFilters SERIALIZATION_FILTERS = new SkipDefaultValuesSerializationFilters();
  private String myName;
  private final LibraryTable myLibraryTable;
  private final Map<OrderRootType, VirtualFilePointerContainer> myRoots;
  private final JarDirectories myJarDirectories = new JarDirectories();
  private final List<LocalFileSystem.WatchRequest> myWatchRequests = new ArrayList<LocalFileSystem.WatchRequest>();
  private final LibraryImpl mySource;
  private LibraryType<?> myType;
  private LibraryProperties myProperties;

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
    readProperties(element);
    readJarDirectories(element);
    //init roots depends on my hashcode, hashcode depends on jardirectories and name
    myRoots = initRoots();
    readRoots(element);
    updateWatchedRoots();
  }

  LibraryImpl(String name, final @Nullable LibraryType<?> type, LibraryTable table, ModifiableRootModel rootModel) {
    myName = name;
    myLibraryTable = table;
    myRootModel = rootModel;
    myType = type;
    if (type != null) {
      myProperties = type.createDefaultProperties();
    }
    myRoots = initRoots();
    mySource = null;
  }

  private Set<OrderRootType> getAllRootTypes() {
    Set<OrderRootType> rootTypes = new HashSet<OrderRootType>();
    rootTypes.addAll(Arrays.asList(OrderRootType.getAllTypes()));
    if (myType != null) {
      rootTypes.addAll(Arrays.asList(myType.getAdditionalRootTypes()));
    }
    return rootTypes;
  }

  private LibraryImpl(LibraryImpl from, LibraryImpl newSource, ModifiableRootModel rootModel) {
    assert !from.isDisposed();
    myRootModel = rootModel;
    myName = from.myName;
    myType = from.myType;
    if (from.myType != null && from.myProperties != null) {
      myProperties = myType.createDefaultProperties();
      //noinspection unchecked
      myProperties.loadState(from.myProperties.getState());
    }
    myRoots = initRoots();
    mySource = newSource;
    myLibraryTable = from.myLibraryTable;
    for (OrderRootType rootType : getAllRootTypes()) {
      final VirtualFilePointerContainer thisContainer = myRoots.get(rootType);
      final VirtualFilePointerContainer thatContainer = from.myRoots.get(rootType);
      thisContainer.addAll(thatContainer);
    }
    myJarDirectories.copyFrom(from.myJarDirectories);
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
        if (myJarDirectories.contains(rootType, file.getUrl())) {
          collectJarFiles(file, expanded, myJarDirectories.isRecursive(rootType, file.getUrl()));
          continue;
        }
      }
      expanded.add(file);
    }
    return VfsUtil.toVirtualFileArray(expanded);
  }

  public static void collectJarFiles(final VirtualFile dir, final List<VirtualFile> container, final boolean recursively) {
    for (VirtualFile child : dir.getChildren()) {
      final VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(child);
      if (jarRoot != null) {
        container.add(jarRoot);
      }
      else {
        if (recursively && child.isDirectory()) {
          collectJarFiles(child, container, recursively);
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

  public List<String> getInvalidRootUrls(OrderRootType type) {
    final List<VirtualFilePointer> pointers = myRoots.get(type).getList();
    List<String> invalidPaths = null;
    for (VirtualFilePointer pointer : pointers) {
      if (!pointer.isValid()) {
        if (invalidPaths == null) {
          invalidPaths = new SmartList<String>();
        }
        invalidPaths.add(pointer.getUrl());
      }
    }
    return invalidPaths != null ? invalidPaths : Collections.<String>emptyList();
  }

  @Override
  public void setProperties(LibraryProperties properties) {
    LOG.assertTrue(isWritable());
    myProperties = properties;
  }

  @NotNull
  public RootProvider getRootProvider() {
    return myRootProvider;
  }

  private Map<OrderRootType, VirtualFilePointerContainer> initRoots() {
    Disposer.register(this, myPointersDisposable);

    Map<OrderRootType, VirtualFilePointerContainer> result = new HashMap<OrderRootType, VirtualFilePointerContainer>(5);

    for (OrderRootType rootType : getAllRootTypes()) {
      result.put(rootType, VirtualFilePointerManager.getInstance().createContainer(myPointersDisposable));
    }
    result.put(OrderRootType.COMPILATION_CLASSES, result.get(OrderRootType.CLASSES));
    result.put(OrderRootType.PRODUCTION_COMPILATION_CLASSES, result.get(OrderRootType.CLASSES));
    result.put(OrderRootType.CLASSES_AND_OUTPUT, result.get(OrderRootType.CLASSES));

    return result;
  }

  public void readExternal(Element element) throws InvalidDataException {
    readName(element);
    readProperties(element);
    readRoots(element);
    readJarDirectories(element);
    updateWatchedRoots();
  }

  private void readProperties(Element element) {
    final String typeId = element.getAttributeValue(LIBRARY_TYPE_ATTR);
    if (typeId == null) return;

    myType = LibraryTypeRegistry.getInstance().findTypeById(typeId);
    if (myType == null) return;

    myProperties = myType.createDefaultProperties();
    final Element propertiesElement = element.getChild(PROPERTIES_ELEMENT);
    if (propertiesElement != null) {
      final Class<?> stateClass = ReflectionUtil.getRawType(ReflectionUtil.resolveVariableInHierarchy(PersistentStateComponent.class.getTypeParameters()[0], myProperties.getClass()));
      //noinspection unchecked
      myProperties.loadState(XmlSerializer.deserialize(propertiesElement, stateClass));
    }
  }

  private void readName(Element element) {
    myName = element.getAttributeValue(LIBRARY_NAME_ATTR);
  }

  private void readRoots(Element element) throws InvalidDataException {
    for (OrderRootType rootType : getAllRootTypes()) {
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
      final OrderRootType rootType = getJarDirectoryRootType(jarDir.getAttributeValue(ROOT_TYPE_ATTR));
      if (url != null) {
        myJarDirectories.add(rootType, url, Boolean.valueOf(Boolean.parseBoolean(recursive)));
      }
    }
  }

  private static OrderRootType getJarDirectoryRootType(@Nullable String type) {
    for (PersistentOrderRootType rootType : OrderRootType.getAllPersistentTypes()) {
      if (rootType.name().equals(type)) {
        return rootType;
      }
    }
    return DEFAULT_JAR_DIRECTORY_TYPE;
  }

  //TODO<rv> Remove the next two methods as a temporary solution. Sort in OrderRootType.
  //
  private static List<OrderRootType> sortRootTypes(Collection<OrderRootType> rootTypes) {
    List<OrderRootType> allTypes = new ArrayList<OrderRootType>(rootTypes);
    Collections.sort(allTypes, new Comparator<OrderRootType>() {
      public int compare(final OrderRootType o1, final OrderRootType o2) {
        return getSortKey(o1).compareTo(getSortKey(o2));
      }
    });
    return allTypes;
  }

  private static String getSortKey(OrderRootType orderRootType) {
    if (orderRootType instanceof PersistentOrderRootType) {
      return ((PersistentOrderRootType)orderRootType).getSdkRootName();
    }
    else if (orderRootType instanceof OrderRootType.DocumentationRootType) {
      return ((OrderRootType.DocumentationRootType)orderRootType).getSdkRootName();
    }
    return "";
  }

  public void writeExternal(Element rootElement) {
    LOG.assertTrue(!isDisposed(), "Already disposed!");

    Element element = new Element(ELEMENT);
    if (myName != null) {
      element.setAttribute(LIBRARY_NAME_ATTR, myName);
    }
    if (myType != null) {
      element.setAttribute(LIBRARY_TYPE_ATTR, myType.getKind().getKindId());
      final Object state = myProperties.getState();
      if (state != null) {
        final Element propertiesElement = XmlSerializer.serialize(state, SERIALIZATION_FILTERS);
        if (propertiesElement != null && (!propertiesElement.getContent().isEmpty() || !propertiesElement.getAttributes().isEmpty())) {
          element.addContent(propertiesElement.setName(PROPERTIES_ELEMENT));
        }
      }
    }
    ArrayList<OrderRootType> storableRootTypes = new ArrayList<OrderRootType>();
    storableRootTypes.addAll(Arrays.asList(OrderRootType.getAllTypes()));
    if (myType != null) {
      storableRootTypes.addAll(Arrays.asList(myType.getAdditionalRootTypes()));
    }
    for (OrderRootType rootType : sortRootTypes(storableRootTypes)) {
      final VirtualFilePointerContainer roots = myRoots.get(rootType);
      if (roots.size() == 0 && rootType.skipWriteIfEmpty()) continue; //compatibility iml/ipr
      final Element rootTypeElement = new Element(rootType.name());
      roots.writeExternal(rootTypeElement, ROOT_PATH_ELEMENT);
      element.addContent(rootTypeElement);
    }
    final List<OrderRootType> rootTypes = sortRootTypes(myJarDirectories.getRootTypes());
    for (OrderRootType rootType : rootTypes) {
      final List<String> urls = new ArrayList<String>(myJarDirectories.getDirectories(rootType));
      Collections.sort(urls, String.CASE_INSENSITIVE_ORDER);
      for (String url : urls) {
        final Element jarDirElement = new Element(JAR_DIRECTORY_ELEMENT);
        jarDirElement.setAttribute(URL_ATTR, url);
        jarDirElement.setAttribute(RECURSIVE_ATTR, Boolean.toString(myJarDirectories.isRecursive(rootType, url)));
        if (!rootType.equals(DEFAULT_JAR_DIRECTORY_TYPE)) {
          jarDirElement.setAttribute(ROOT_TYPE_ATTR, rootType.name());
        }
        element.addContent(jarDirElement);
      }
    }
    rootElement.addContent(element);
  }

  private boolean isWritable() {
    return mySource != null;
  }

  @Override
  public LibraryType<?> getType() {
    return myType;
  }

  @Override
  public LibraryProperties getProperties() {
    return myProperties;
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
    addJarDirectory(url, recursive, DEFAULT_JAR_DIRECTORY_TYPE);
  }

  public void addJarDirectory(@NotNull final VirtualFile file, final boolean recursive) {
    addJarDirectory(file, recursive, DEFAULT_JAR_DIRECTORY_TYPE);
  }

  public void addJarDirectory(@NotNull final String url, final boolean recursive, @NotNull OrderRootType rootType) {
    assert !isDisposed();
    LOG.assertTrue(isWritable());
    final VirtualFilePointerContainer container = myRoots.get(rootType);
    container.add(url);
    myJarDirectories.add(rootType, url, recursive);
  }

  public void addJarDirectory(@NotNull final VirtualFile file, final boolean recursive, @NotNull OrderRootType rootType) {
    assert !isDisposed();
    LOG.assertTrue(isWritable());
    final VirtualFilePointerContainer container = myRoots.get(rootType);
    container.add(file);
    myJarDirectories.add(rootType, file.getUrl(), recursive);
  }

  public boolean isJarDirectory(@NotNull final String url) {
    return isJarDirectory(url, DEFAULT_JAR_DIRECTORY_TYPE);
  }

  public boolean isJarDirectory(@NotNull final String url, @NotNull final OrderRootType rootType) {
    return myJarDirectories.contains(rootType, url);
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
      myJarDirectories.remove(rootType, url);
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
    myProperties = fromModel.myProperties;
    if (areRootsChanged(fromModel)) {
      disposeMyPointers();
      copyRootsFrom(fromModel);
      myJarDirectories.copyFrom(fromModel.myJarDirectories);
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
      for (OrderRootType rootType : myJarDirectories.getRootTypes()) {
        for (String url : myJarDirectories.getDirectories(rootType)) {
          if (fm.getFileSystem(VirtualFileManager.extractProtocol(url)) instanceof LocalFileSystem) {
            final boolean watchRecursively = myJarDirectories.isRecursive(rootType, url);
            final LocalFileSystem.WatchRequest request = fs.addRootToWatch(VirtualFileManager.extractPath(url), watchRecursively);
            myWatchRequests.add(request);
          }
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
            for (String rootUrl : myJarDirectories.getAllDirectories()) {
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
    if (myType != null ? !myType.equals(library.myType) : library.myType != null) return false;
    if (myProperties != null ? !myProperties.equals(library.myProperties) : library.myProperties != null) return false;

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
    return "Library: name:" + myName + "; jars:" + myJarDirectories + "; roots:" + myRoots.values();
  }

  @Nullable("will return non-null value only for module level libraries")
  public Module getModule() {
    return myRootModel == null ? null : myRootModel.getModule();
  }
}
