/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.openapi.roots.impl.storage;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.StateStorageException;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.impl.ProjectMacrosUtil;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.tracker.VirtualFileTracker;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.fs.FileSystem;
import com.intellij.util.io.fs.IFile;
import com.intellij.util.messages.MessageBus;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Vladislav.Kaznacheev
 * Date: Mar 9, 2007
 * Time: 1:42:06 PM
 */
public class ClasspathStorage implements StateStorage {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.storage.ClasspathStorage");

  @NonNls public static final String SPECIAL_STORAGE = "special";

  public static final String DEFAULT_STORAGE_DESCR = ProjectBundle.message("project.roots.classpath.format.default.descr");

  @NonNls public static final String CLASSPATH_DIR_OPTION = JpsProjectLoader.CLASSPATH_DIR_ATTRIBUTE;

  @NonNls private static final String COMPONENT_TAG = "component";
  private Object mySession;
  private final ClasspathStorageProvider.ClasspathConverter myConverter;


  public ClasspathStorage(Module module) {
    myConverter = getProvider(ClassPathStorageUtil.getStorageType(module)).createConverter(module);
    final MessageBus messageBus = module.getMessageBus();
    final VirtualFileTracker virtualFileTracker =
      (VirtualFileTracker)module.getPicoContainer().getComponentInstanceOfType(VirtualFileTracker.class);
    if (virtualFileTracker != null && messageBus != null) {
      final ArrayList<VirtualFile> files = new ArrayList<VirtualFile>();
      try {
        myConverter.getFileSet().listFiles(files);
        for (VirtualFile file : files) {
          final Listener listener = messageBus.syncPublisher(STORAGE_TOPIC);
          virtualFileTracker.addTracker(file.getUrl(), new VirtualFileAdapter() {
            @Override
            public void contentsChanged(final VirtualFileEvent event) {
              listener.storageFileChanged(event, ClasspathStorage.this);
            }
          }, true, module);
        }
      }
      catch (UnsupportedOperationException e) {
        //UnsupportedStorageProvider doesn't mean any files
      }
    }
  }

  private FileSet getFileSet() {
    return myConverter.getFileSet();
  }

  @Override
  @Nullable
  public <T> T getState(final Object component, final String componentName, Class<T> stateClass, @Nullable T mergeInto)
    throws StateStorageException {
    assert component instanceof ModuleRootManager;
    assert componentName.equals("NewModuleRootManager");
    assert stateClass == ModuleRootManagerImpl.ModuleRootManagerState.class;

    try {
      final Module module = ((ModuleRootManagerImpl)component).getModule();
      final Element element = new Element(COMPONENT_TAG);
      final Set<String> macros;
      ModifiableRootModel model = null;
      try {
        model = ((ModuleRootManagerImpl)component).getModifiableModel();
        macros = myConverter.getClasspath(model, element);
      }
      finally {
        if (model != null) {
          model.dispose();
        }
      }

      final boolean macrosOk = ProjectMacrosUtil.checkNonIgnoredMacros(module.getProject(), macros);
      PathMacroManager.getInstance(module).expandPaths(element);
      ModuleRootManagerImpl.ModuleRootManagerState moduleRootManagerState = new ModuleRootManagerImpl.ModuleRootManagerState();
      moduleRootManagerState.readExternal(element);
      if (!macrosOk) {
        throw new StateStorageException(ProjectBundle.message("project.load.undefined.path.variables.error"));
      }
      //noinspection unchecked
      return (T)moduleRootManagerState;
    }
    catch (InvalidDataException e) {
      throw new StateStorageException(e.getMessage());
    }
    catch (IOException e) {
      throw new StateStorageException(e.getMessage());
    }
  }

  @Override
  public boolean hasState(final Object component, final String componentName, final Class<?> aClass, final boolean reloadData)
    throws StateStorageException {
    return true;
  }

  public void setState(@NotNull Object component, @NotNull String componentName, @NotNull Object state) throws StateStorageException {
    assert component instanceof ModuleRootManager;
    assert componentName.equals("NewModuleRootManager");
    assert state.getClass() == ModuleRootManagerImpl.ModuleRootManagerState.class;

    try {
      myConverter.setClasspath((ModuleRootManagerImpl)component);
    }
    catch (WriteExternalException e) {
      throw new StateStorageException(e.getMessage());
    }
    catch (IOException e) {
      throw new StateStorageException(e.getMessage());
    }
  }

  @Override
  @NotNull
  public ExternalizationSession startExternalization() {
    final ExternalizationSession session = new ExternalizationSession() {
      @Override
      public void setState(@NotNull final Object component, final String componentName, @NotNull final Object state, final Storage storageSpec)
        throws StateStorageException {
        assert mySession == this;
        ClasspathStorage.this.setState(component, componentName, state);
      }
    };

    mySession = session;
    return session;
  }

  @Override
  @NotNull
  public SaveSession startSave(@NotNull final ExternalizationSession externalizationSession) {
    assert mySession == externalizationSession;

    final SaveSession session = new MySaveSession();

    mySession = session;
    return session;
  }

  private static void convert2Io(List<IFile> list, ArrayList<VirtualFile> virtualFiles) {
    for (VirtualFile virtualFile : virtualFiles) {
      final File ioFile = VfsUtilCore.virtualToIoFile(virtualFile);
      list.add(FileSystem.FILE_SYSTEM.createFile(ioFile.getAbsolutePath()));
    }
  }

  @Override
  public void finishSave(@NotNull final SaveSession saveSession) {
    try {
      LOG.assertTrue(mySession == saveSession);
    }
    finally {
      mySession = null;
    }
  }

  @Override
  public void reload(@NotNull final Set<String> changedComponents) throws StateStorageException {
  }

  public boolean needsSave() throws StateStorageException {
    return getFileSet().hasChanged();
  }

  public void save() throws StateStorageException {
    final Ref<IOException> ref = new Ref<IOException>();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          getFileSet().commit();
        }
        catch (IOException e) {
          ref.set(e);
        }
      }
    });

    if (!ref.isNull()) {
      throw new StateStorageException(ref.get());
    }
  }

  @NotNull
  public static ClasspathStorageProvider getProvider(@NotNull String type) {
    for (ClasspathStorageProvider provider : getProviders()) {
      if (type.equals(provider.getID())) {
        return provider;
      }
    }
    return new UnsupportedStorageProvider(type);
  }

  @NotNull
  public static List<ClasspathStorageProvider> getProviders() {
    final List<ClasspathStorageProvider> list = new ArrayList<ClasspathStorageProvider>();
    list.add(new DefaultStorageProvider());
    ContainerUtil.addAll(list, Extensions.getExtensions(ClasspathStorageProvider.EXTENSION_POINT_NAME));
    return list;
  }

  @NotNull
  public static String getModuleDir(@NotNull Module module) {
    return new File(module.getModuleFilePath()).getParent();
  }

  public static String getStorageRootFromOptions(final Module module) {
    final String moduleRoot = getModuleDir(module);
    final String storageRef = module.getOptionValue(CLASSPATH_DIR_OPTION);
    if (storageRef == null) {
      return moduleRoot;
    }
    else if (FileUtil.isAbsolute(storageRef)) {
      return storageRef;
    }
    else {
      return FileUtil.toSystemIndependentName(new File(moduleRoot, storageRef).getPath());
    }
  }

  public static void setStorageType(@NotNull ModuleRootModel model, @NotNull String storageID) {
    final Module module = model.getModule();
    final String oldStorageType = ClassPathStorageUtil.getStorageType(module);
    if (oldStorageType.equals(storageID)) {
      return;
    }

    getProvider(oldStorageType).detach(module);

    if (storageID.equals(ClassPathStorageUtil.DEFAULT_STORAGE)) {
      module.clearOption(ClassPathStorageUtil.CLASSPATH_OPTION);
      module.clearOption(CLASSPATH_DIR_OPTION);
    }
    else {
      module.setOption(ClassPathStorageUtil.CLASSPATH_OPTION, storageID);
      module.setOption(CLASSPATH_DIR_OPTION, getProvider(storageID).getContentRoot(model));
    }
  }

  public static void moduleRenamed(Module module, String newName) {
    getProvider(ClassPathStorageUtil.getStorageType(module)).moduleRenamed(module, newName);
  }

  public static void modulePathChanged(Module module, String path) {
    getProvider(ClassPathStorageUtil.getStorageType(module)).modulePathChanged(module, path);
  }

  private static class DefaultStorageProvider implements ClasspathStorageProvider {
    @Override
    @NonNls
    public String getID() {
      return ClassPathStorageUtil.DEFAULT_STORAGE;
    }

    @Override
    @Nls
    public String getDescription() {
      return DEFAULT_STORAGE_DESCR;
    }

    @Override
    public void assertCompatible(final ModuleRootModel model) throws ConfigurationException {
    }

    @Override
    public void detach(Module module) {
    }

    @Override
    public void moduleRenamed(Module module, String newName) {
      //do nothing
    }

    @Override
    public ClasspathConverter createConverter(Module module) {
      throw new UnsupportedOperationException(getDescription());
    }

    @Override
    public String getContentRoot(ModuleRootModel model) {
      return null;
    }

    @Override
    public void modulePathChanged(Module module, String path) {
    }
  }

  public static class UnsupportedStorageProvider implements ClasspathStorageProvider {
    private final String myType;

    public UnsupportedStorageProvider(final String type) {
      myType = type;
    }

    @Override
    @NonNls
    public String getID() {
      return myType;
    }

    @Override
    @Nls
    public String getDescription() {
      return "Unsupported classpath format " + myType;
    }

    @Override
    public void assertCompatible(final ModuleRootModel model) throws ConfigurationException {
      throw new UnsupportedOperationException(getDescription());
    }

    @Override
    public void detach(final Module module) {
      throw new UnsupportedOperationException(getDescription());
    }

    @Override
    public void moduleRenamed(Module module, String newName) {
      throw new UnsupportedOperationException(getDescription());
    }

    @Override
    public ClasspathConverter createConverter(final Module module) {
      return new ClasspathConverter() {
        @Override
        public FileSet getFileSet() {
          throw new StateStorageException(getDescription());
        }

        @Override
        public Set<String> getClasspath(final ModifiableRootModel model, final Element element) throws IOException, InvalidDataException {
          throw new InvalidDataException(getDescription());
        }

        @Override
        public void setClasspath(ModuleRootModel model) throws IOException, WriteExternalException {
          throw new WriteExternalException(getDescription());
        }
      };
    }

    @Override
    public String getContentRoot(ModuleRootModel model) {
      return null;
    }

    @Override
    public void modulePathChanged(Module module, String path) {
      throw new UnsupportedOperationException(getDescription());
    }
  }

  private class MySaveSession implements SaveSession, SafeWriteRequestor {
    public boolean needsSave() throws StateStorageException {
      assert mySession == this;
      return ClasspathStorage.this.needsSave();
    }

    @Override
    public void save() throws StateStorageException {
      assert mySession == this;
      ClasspathStorage.this.save();
    }

    @Override
    @Nullable
    public Set<String> analyzeExternalChanges(@NotNull final Set<Pair<VirtualFile, StateStorage>> changedFiles) {
      return null;
    }

    @NotNull
    @Override
    public Collection<IFile> getStorageFilesToSave() throws StateStorageException {
      if (needsSave()) {
        final List<IFile> list = new ArrayList<IFile>();
        final ArrayList<VirtualFile> virtualFiles = new ArrayList<VirtualFile>();
        getFileSet().listModifiedFiles(virtualFiles);
        convert2Io(list, virtualFiles);
        return list;
      }
      else {
        return Collections.emptyList();
      }
    }

    @NotNull
    @Override
    public List<IFile> getAllStorageFiles() {
      final List<IFile> list = new ArrayList<IFile>();
      final ArrayList<VirtualFile> virtualFiles = new ArrayList<VirtualFile>();
      getFileSet().listFiles(virtualFiles);
      convert2Io(list, virtualFiles);
      return list;
    }
  }
}
