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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


@State(
  name = "NewModuleRootManager",
  storages = {
    @Storage(
      id = ClasspathStorage.DEFAULT_STORAGE,
      file = "$MODULE_FILE$"
    ),

    @Storage(
          id = ClasspathStorage.SPECIAL_STORAGE,
          storageClass = ClasspathStorage.class
    )
  },
  storageChooser = ModuleRootManagerImpl.StorageChooser.class
)
public class ModuleRootManagerImpl extends ModuleRootManager implements ModuleComponent, PersistentStateComponent<ModuleRootManagerImpl.ModuleRootManagerState> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.ModuleRootManagerImpl");

  private final Module myModule;
  private final ProjectRootManagerImpl myProjectRootManager;
  private final VirtualFilePointerManager myFilePointerManager;
  private RootModelImpl myRootModel;
  private final ModuleFileIndexImpl myFileIndex;
  private boolean myIsDisposed = false;
  private boolean isModuleAdded = false;
  private final Map<OrderRootType, Set<VirtualFilePointer>> myCachedFiles = new THashMap<OrderRootType, Set<VirtualFilePointer>>();
  private final Map<OrderRootType, Set<VirtualFilePointer>> myCachedExportedFiles = new THashMap<OrderRootType, Set<VirtualFilePointer>>();
  private final Map<RootModelImpl, Throwable> myModelCreations = new THashMap<RootModelImpl, Throwable>();


  public ModuleRootManagerImpl(Module module,
                               DirectoryIndex directoryIndex,
                               ProjectRootManagerImpl projectRootManager,
                               VirtualFilePointerManager filePointerManager) {
    myModule = module;
    myProjectRootManager = projectRootManager;
    myFilePointerManager = filePointerManager;

    myFileIndex = new ModuleFileIndexImpl(myModule, directoryIndex);

    myRootModel = new RootModelImpl(this, myProjectRootManager, myFilePointerManager);
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @NotNull
  public ModuleFileIndex getFileIndex() {
    return myFileIndex;
  }

  @NotNull
  public String getComponentName() {
    return "NewModuleRootManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    myRootModel.dispose();
    myIsDisposed = true;

    if (Disposer.isDebugMode()) {
      final Set<Map.Entry<RootModelImpl, Throwable>> entries = myModelCreations.entrySet();
      for (final Map.Entry<RootModelImpl, Throwable> entry : new ArrayList<Map.Entry<RootModelImpl, Throwable>>(entries)) {
        System.err.println("***********************************************************************************************");
        System.err.println("***                        R O O T   M O D E L   N O T   D I S P O S E D                    ***");
        System.err.println("***********************************************************************************************");
        System.err.println("Created at:");
        entry.getValue().printStackTrace(System.err);
        entry.getKey().dispose();
      }
    }
  }



  public VirtualFile getExplodedDirectory() {
    return myRootModel.getExplodedDirectory();
  }

  public String getExplodedDirectoryUrl() {
    return myRootModel.getExplodedDirectoryUrl();
  }

  @NotNull
  public ModifiableRootModel getModifiableModel() {
    return getModifiableModel(new RootConfigurationAccessor());
  }

  @NotNull
  public ModifiableRootModel getModifiableModel(final RootConfigurationAccessor accessor) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    final RootModelImpl model = new RootModelImpl(myRootModel, this, true, accessor, null, myFilePointerManager, myProjectRootManager) {
      @Override
      public void dispose() {
        super.dispose();
        if (Disposer.isDebugMode()) {
          myModelCreations.remove(this);
        }

        for (OrderEntry entry : ModuleRootManagerImpl.this.getOrderEntries()) {
          assert !((RootModelComponentBase)entry).isDisposed();
        }
      }
    };
    if (Disposer.isDebugMode()) {
      myModelCreations.put(model, new Throwable());
    }
    return model;
  }

  void makeRootsChange(@NotNull Runnable runnable) {
    ProjectRootManagerEx projectRootManagerEx = (ProjectRootManagerEx)ProjectRootManager.getInstance(myModule.getProject());
    // IMPORTANT: should be the first listener!
    projectRootManagerEx.makeRootsChange(runnable, false, isModuleAdded);
  }

  RootModelImpl getRootModel() {
    return myRootModel;
  }

  public ContentEntry[] getContentEntries() {
    return myRootModel.getContentEntries();
  }

  @NotNull
  public OrderEntry[] getOrderEntries() {
    return myRootModel.getOrderEntries();
  }

  public Sdk getSdk() {
    return myRootModel.getSdk();
  }

  public boolean isSdkInherited() {
    return myRootModel.isSdkInherited();
  }

  @NotNull
  public VirtualFile[] getFiles(OrderRootType type) {
    return getFiles(type, new HashSet<Module>());
  }

  @NotNull
  private VirtualFile[] getFiles(OrderRootType type, Set<Module> processed) {
    Set<VirtualFilePointer> cachedFiles = myCachedFiles.get(type);
    if (cachedFiles == null) {
      cachedFiles = new LinkedHashSet<VirtualFilePointer>();
      final Iterator<OrderEntry> orderIterator = myRootModel.getOrderIterator();
      while (orderIterator.hasNext()) {
        OrderEntry entry = orderIterator.next();
        final String [] urls;
        if (entry instanceof ModuleOrderEntryImpl) {
          List<String> list = ((ModuleOrderEntryImpl)entry).getUrls(type, processed);
          urls = ArrayUtil.toStringArray(list);
        }
        else {
          urls = entry.getUrls(type);
        }
        final VirtualFilePointerManager virtualFilePointerManager = VirtualFilePointerManager.getInstance();
        for (String url : urls) {
          if (url != null) {
            cachedFiles.add(virtualFilePointerManager.create(url, getModule(), null));
          }
        }
      }
      myCachedFiles.put(type, cachedFiles);
    }
    return convertPointers(cachedFiles);
  }

  private static VirtualFile[] convertPointers(final Set<VirtualFilePointer> cachedFiles) {
    final LinkedHashSet<VirtualFile> result = new LinkedHashSet<VirtualFile>();
    for (VirtualFilePointer cachedFile : cachedFiles) {
      final VirtualFile virtualFile = cachedFile.getFile();
      if (virtualFile != null) {
        result.add(virtualFile);
      }
    }

    return VfsUtil.toVirtualFileArray(result);
  }

  @NotNull
  public String[] getUrls(OrderRootType type) {
    List<String> urls = getUrls(type, null);
    return ArrayUtil.toStringArray(urls);
  }

  @NotNull
  private List<String> getUrls(OrderRootType type, @Nullable Set<Module> processed) {
    final ArrayList<String> result = new ArrayList<String>();
    final Iterator orderIterator = myRootModel.getOrderIterator();
    while (orderIterator.hasNext()) {
      final OrderEntry entry = (OrderEntry)orderIterator.next();
      if (entry instanceof ModuleOrderEntryImpl) {
        result.addAll(((ModuleOrderEntryImpl)entry).getUrls(type, processed));
      }
      else {
        final String[] urls = entry.getUrls(type);
        result.addAll(Arrays.asList(urls));
      }
    }
    return result;
  }

  void commitModel(RootModelImpl rootModel) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    LOG.assertTrue(rootModel.myModuleRootManager == this);

    final Project project = myModule.getProject();
    final ModifiableModuleModel moduleModel = ModuleManager.getInstance(project).getModifiableModel();
    multiCommit(new ModifiableRootModel[]{rootModel}, moduleModel);
  }

  private static void commitModelWithoutEvents(RootModelImpl rootModel) {
    doCommit(rootModel);
  }

  private static void doCommit(RootModelImpl rootModel) {
    rootModel.docommit();
    rootModel.dispose();
  }


  static void multiCommit(ModifiableRootModel[] rootModels,
                          ModifiableModuleModel moduleModel) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    final List<RootModelImpl> modelsToCommit = getSortedChangedModels(rootModels, moduleModel);

    final List<ModifiableRootModel> modelsToDispose = new ArrayList<ModifiableRootModel>(Arrays.asList(rootModels));
    modelsToDispose.removeAll(modelsToCommit);

    Runnable runnable = new Runnable() {
      public void run() {
        for (RootModelImpl rootModel : modelsToCommit) {
          commitModelWithoutEvents(rootModel);
        }

        for (ModifiableRootModel model : modelsToDispose) {
          model.dispose();
        }
      }
    };
    ModuleManagerImpl.commitModelWithRunnable(moduleModel, runnable);

  }

  private static List<RootModelImpl> getSortedChangedModels(ModifiableRootModel[] _rootModels,
                                                    final ModifiableModuleModel moduleModel) {
    List<RootModelImpl> rootModels = new ArrayList<RootModelImpl>();
    for (ModifiableRootModel _rootModel : _rootModels) {
      RootModelImpl rootModel = (RootModelImpl)_rootModel;
      if (rootModel.isChanged()) {
        rootModels.add(rootModel);
      }
    }

    sortRootModels(rootModels, moduleModel);
    return rootModels;
  }

  @NotNull
  public Module[] getDependencies() {
    return myRootModel.getModuleDependencies();
  }

  @NotNull
  @Override
  public Module[] getDependencies(boolean includeTests) {
    return myRootModel.getModuleDependencies(includeTests);
  }

  public boolean isDependsOn(Module module) {
    return myRootModel.isDependsOn(module);
  }

  @NotNull
  public String[] getDependencyModuleNames() {
    return myRootModel.getDependencyModuleNames();
  }

  @NotNull
  public VirtualFile[] getRootPaths(final OrderRootType rootType) {
    return myRootModel.getRootPaths(rootType);
  }

  @NotNull
  public String[] getRootUrls(final OrderRootType rootType) {
    return myRootModel.getRootUrls(rootType);
  }

  public <T> T getModuleExtension(final Class<T> klass) {
    return myRootModel.getModuleExtension(klass);
  }

  public <R> R processOrder(RootPolicy<R> policy, R initialValue) {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.processOrder(policy, initialValue);
  }

  @NotNull
  @Override
  public OrderEnumerator orderEntries() {
    return myRootModel.orderEntries();
  }

  List<String> getUrlsForOtherModules(OrderRootType rootType, Set<Module> processed) {
    List<String> result = new ArrayList<String>();
    if (OrderRootType.SOURCES.equals(rootType) || OrderRootType.COMPILATION_CLASSES.equals(rootType)
        || OrderRootType.PRODUCTION_COMPILATION_CLASSES.equals(rootType) || OrderRootType.CLASSES.equals(rootType)) {
      addExportedUrls(myRootModel, rootType, result, processed);
      return result;
    }
    else if (OrderRootType.CLASSES_AND_OUTPUT.equals(rootType)) {
      return getUrls(OrderRootType.CLASSES_AND_OUTPUT, processed);
    }
    return Collections.emptyList();
  }

  private static void addExportedUrls(RootModelImpl model, OrderRootType type, List<String> result, Set<Module> processed) {
    for (final OrderEntry orderEntry : model.getOrderEntries()) {
      if (orderEntry instanceof ModuleSourceOrderEntryImpl) {
        ((ModuleSourceOrderEntryImpl)orderEntry).addExportedUrls(type, result);
      }
      else if (orderEntry instanceof ExportableOrderEntry && ((ExportableOrderEntry)orderEntry).isExported()) {
        if (orderEntry instanceof ModuleOrderEntryImpl) {
          result.addAll(((ModuleOrderEntryImpl)orderEntry).getUrls(type, processed));
        }
        else {
          result.addAll(Arrays.asList(orderEntry.getUrls(type)));
        }
      }
    }
  }

  @NotNull
  VirtualFile[] getFilesForOtherModules(OrderRootType rootType, Set<Module> processed) {
    Set<VirtualFilePointer> filePointers = myCachedExportedFiles.get(rootType);
    if (filePointers == null) {
      filePointers = new LinkedHashSet<VirtualFilePointer>();
      List<String> result = new ArrayList<String>();
      if (OrderRootType.SOURCES.equals(rootType) || OrderRootType.COMPILATION_CLASSES.equals(rootType)
          || OrderRootType.PRODUCTION_COMPILATION_CLASSES.equals(rootType) || OrderRootType.CLASSES.equals(rootType)) {
        addExportedUrls(myRootModel, rootType, result, processed);
      }
      else if (OrderRootType.CLASSES_AND_OUTPUT.equals(rootType)) {
        return getFiles(OrderRootType.CLASSES_AND_OUTPUT, processed);
      }
      else if (rootType.collectFromDependentModules()) {
        return getFiles(rootType, processed);
      }
      else {
        return VirtualFile.EMPTY_ARRAY;
      }
      final VirtualFilePointerManager pointerManager = VirtualFilePointerManager.getInstance();
      for (String url : result) {
        if (url != null) {
          filePointers.add(pointerManager.create(url, getModule(), null));
        }
      }
      myCachedExportedFiles.put(rootType, filePointers);
    }

    return convertPointers(filePointers);
  }

  @NotNull
  public VirtualFile[] getContentRoots() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getContentRoots();
  }

  @NotNull
  public String[] getContentRootUrls() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getContentRootUrls();
  }

  @NotNull
  public String[] getExcludeRootUrls() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getExcludeRootUrls();
  }

  @NotNull
  public VirtualFile[] getExcludeRoots() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getExcludeRoots();
  }

  @NotNull
  public String[] getSourceRootUrls() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getSourceRootUrls();
  }

  @NotNull
  public VirtualFile[] getSourceRoots() {
    LOG.assertTrue(!myIsDisposed);
    return myRootModel.getSourceRoots();
  }

  public void projectOpened() {
    myRootModel.projectOpened();
  }

  public void projectClosed() {
    myRootModel.projectClosed();
  }

  public void moduleAdded() {
    isModuleAdded = true;
  }


  private static void sortRootModels(List<RootModelImpl> rootModels, final ModifiableModuleModel moduleModel) {
    DFSTBuilder<RootModelImpl> builder = createDFSTBuilder(rootModels, moduleModel);

    final Comparator<RootModelImpl> comparator = builder.comparator();
    Collections.sort(rootModels, comparator);
  }

  private static DFSTBuilder<RootModelImpl> createDFSTBuilder(List<RootModelImpl> rootModels, final ModifiableModuleModel moduleModel) {
    final Map<String, RootModelImpl> nameToModel = new HashMap<String, RootModelImpl>();
    for (final RootModelImpl rootModel : rootModels) {
      final String name = rootModel.getModule().getName();
      LOG.assertTrue(!nameToModel.containsKey(name));
      nameToModel.put(name, rootModel);
    }
    final Module[] modules = moduleModel.getModules();
    for (final Module module : modules) {
      final String name = module.getName();
      if (!nameToModel.containsKey(name)) {
        final RootModelImpl rootModel = ((ModuleRootManagerImpl)ModuleRootManager.getInstance(module)).myRootModel;
        nameToModel.put(name, rootModel);
      }
    }
    final Collection<RootModelImpl> allRootModels = nameToModel.values();
    return new DFSTBuilder<RootModelImpl>(new GraphGenerator<RootModelImpl>(new CachingSemiGraph<RootModelImpl>(new GraphGenerator.SemiGraph<RootModelImpl>() {
          public Collection<RootModelImpl> getNodes() {
            return allRootModels;
          }

          public Iterator<RootModelImpl> getIn(RootModelImpl rootModel) {
            final ArrayList<String> names1 = rootModel.processOrder(new RootPolicy<ArrayList<String>>() {
              public ArrayList<String> visitModuleOrderEntry(ModuleOrderEntry moduleOrderEntry, ArrayList<String> strings) {
                final Module module = moduleOrderEntry.getModule();
                if (module != null && !module.isDisposed()) {
                  strings.add(module.getName());
                } else {
                  final Module moduleToBeRenamed = moduleModel.getModuleToBeRenamed(moduleOrderEntry.getModuleName());
                  if (moduleToBeRenamed != null && !moduleToBeRenamed.isDisposed()) {
                    strings.add(moduleToBeRenamed.getName());
                  }
                }
                return strings;
              }
            }, new ArrayList<String>());

            final String[] names = ArrayUtil.toStringArray(names1);
            List<RootModelImpl> result = new ArrayList<RootModelImpl>();
            for (String name : names) {
              final RootModelImpl depRootModel = nameToModel.get(name);
              if (depRootModel != null) { // it is ok not to find one
                result.add(depRootModel);
              }
            }
            return result.iterator();
          }
        })));
  }


  public void dropCaches() {
    myCachedFiles.clear();
    myCachedExportedFiles.clear();
  }

  public ModuleRootManagerState getState() {
    return new ModuleRootManagerState(myRootModel);
  }

  public void loadState(ModuleRootManagerState object) {
    try {
      final RootModelImpl newModel = new RootModelImpl(object.getRootModelElement(), this, myProjectRootManager, myFilePointerManager);

      boolean throwEvent = myRootModel != null;

      if (throwEvent) {
        makeRootsChange(new Runnable() {
          public void run() {
            doCommit(newModel);
          }
        });
      }
      else {
        myRootModel = newModel;
      }

      assert !myRootModel.isOrderEntryDisposed();
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public static class ModuleRootManagerState implements JDOMExternalizable {
    private RootModelImpl myRootModel;
    private Element myRootModelElement = null;

    public ModuleRootManagerState() {
    }

    public ModuleRootManagerState(final RootModelImpl rootModel) {
      myRootModel = rootModel;
    }

    public void readExternal(Element element) throws InvalidDataException {
      myRootModelElement = element;
    }

    public void writeExternal(Element element) throws WriteExternalException {
      myRootModel.writeExternal(element);
    }

    public Element getRootModelElement() {
      return myRootModelElement;
    }
  }

  public static class StorageChooser implements StateStorageChooser<ModuleRootManagerImpl> {
    public Storage[] selectStorages(Storage[] storages, ModuleRootManagerImpl moduleRootManager, final StateStorageOperation operation) {
      final String storageType = ClasspathStorage.getStorageType(moduleRootManager.getModule());
      final String id = storageType.equals(ClasspathStorage.DEFAULT_STORAGE)? ClasspathStorage.DEFAULT_STORAGE: ClasspathStorage.SPECIAL_STORAGE;
      for (Storage storage : storages) {
        if (storage.id().equals(id)) return new Storage[]{storage};
      }
      throw new IllegalArgumentException();
    }
  }
}
