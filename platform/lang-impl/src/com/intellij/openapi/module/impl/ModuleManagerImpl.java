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

package com.intellij.openapi.module.impl;

import com.intellij.ProjectTopics;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.*;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.impl.ProjectLifecycleListener;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.MessageHandler;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author max
 */
@State(
  name = ModuleManagerImpl.COMPONENT_NAME,
  storages = {
    @Storage(id = "default", file = "$PROJECT_FILE$")
   ,@Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/modules.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class ModuleManagerImpl extends ModuleManager implements ProjectComponent, PersistentStateComponent<Element>, ModificationTracker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.module.impl.ModuleManagerImpl");
  public static final Key<String> DISPOSED_MODULE_NAME = Key.create("DisposedNeverAddedModuleName");
  private final Project myProject;
  private volatile ModuleModelImpl myModuleModel = new ModuleModelImpl();

  @NonNls public static final String COMPONENT_NAME = "ProjectModuleManager";
  private static final String MODULE_GROUP_SEPARATOR = "/";
  private List<ModulePath> myModulePaths;
  private final List<ModulePath> myFailedModulePaths = new ArrayList<ModulePath>();
  @NonNls public static final String ELEMENT_MODULES = "modules";
  @NonNls public static final String ELEMENT_MODULE = "module";
  @NonNls private static final String ATTRIBUTE_FILEURL = "fileurl";
  @NonNls public static final String ATTRIBUTE_FILEPATH = "filepath";
  @NonNls private static final String ATTRIBUTE_GROUP = "group";
  private long myModificationCount;
  private final MessageBusConnection myConnection;
  private final MessageBus myMessageBus;

  public static ModuleManagerImpl getInstanceImpl(Project project) {
    return (ModuleManagerImpl)getInstance(project);
  }

  private void cleanCachedStuff() {
    myCachedModuleComparator = null;
    myCachedSortedModules = null;
  }

  public ModuleManagerImpl(Project project, MessageBus bus) {
    myProject = project;
    myMessageBus = bus;
    myConnection = bus.connect(project);

    myConnection.setDefaultHandler(new MessageHandler() {
      public void handle(Method event, Object... params) {
        cleanCachedStuff();
      }
    });

    myConnection.subscribe(ProjectTopics.PROJECT_ROOTS);
    myConnection.subscribe(ProjectLifecycleListener.TOPIC, new ProjectLifecycleListener.Adapter() {
      public void projectComponentsInitialized(final Project project) {
        loadModules(myModuleModel);
      }
    });

  }


  @NotNull
  public String getComponentName() {
    return COMPONENT_NAME;
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    myModuleModel.disposeModel();
  }

  public long getModificationCount() {
    return myModificationCount;
  }

  public Element getState() {
    final Element e = new Element("state");
    writeExternal(e);
    return e;
  }

  public void loadState(Element state) {
    List<ModulePath> prevPaths = myModulePaths;
    readExternal(state);
    if (prevPaths != null) {
      ModifiableModuleModel model = getModifiableModel();

      Module[] existingModules = model.getModules();


      for (Module existingModule : existingModules) {
        ModulePath correspondingPath = findCorrespondingPath(existingModule);
        if (correspondingPath == null) {
          model.disposeModule(existingModule);
        }
        else {
          myModulePaths.remove(correspondingPath);

          String groupStr = correspondingPath.getModuleGroup();
          String[] group = groupStr != null ? groupStr.split(MODULE_GROUP_SEPARATOR) : null;
          if (!Arrays.equals(group, model.getModuleGroupPath(existingModule))) {
            model.setModuleGroupPath(existingModule,  group);
          }
        }
      }

      loadModules((ModuleModelImpl)model);

      model.commit();
    }
  }

  private ModulePath findCorrespondingPath(final Module existingModule) {
    for (ModulePath modulePath : myModulePaths) {
      if (modulePath.getPath().equals(existingModule.getModuleFilePath())) return modulePath;
    }

    return null;
  }

  public static final class ModulePath {
    private final String myPath;
    private final String myModuleGroup;

    public ModulePath(String path, String moduleGroup) {
      myPath = path;
      myModuleGroup = moduleGroup;
    }

    public String getPath() {
      return myPath;
    }

    public String getModuleGroup() {
      return myModuleGroup;
    }
  }

  public static ModulePath[] getPathsToModuleFiles(Element element) {
    final List<ModulePath> paths = new ArrayList<ModulePath>();
    final Element modules = element.getChild(ELEMENT_MODULES);
    if (modules != null) {
      for (final Object value : modules.getChildren(ELEMENT_MODULE)) {
        Element moduleElement = (Element)value;
        final String fileUrlValue = moduleElement.getAttributeValue(ATTRIBUTE_FILEURL);
        final String filepath;
        if (fileUrlValue != null) {
          filepath = VirtualFileManager.extractPath(fileUrlValue).replace('/', File.separatorChar);
        }
        else {
          // [dsl] support for older formats
          filepath = moduleElement.getAttributeValue(ATTRIBUTE_FILEPATH).replace('/', File.separatorChar);
        }
        final String group = moduleElement.getAttributeValue(ATTRIBUTE_GROUP);
        paths.add(new ModulePath(filepath, group));
      }
    }
    return paths.toArray(new ModulePath[paths.size()]);
  }

  public void readExternal(final Element element) {
    myModulePaths = new ArrayList<ModulePath>(Arrays.asList(getPathsToModuleFiles(element)));
  }

  private void loadModules(final ModuleModelImpl moduleModel) {
    if (myModulePaths != null && myModulePaths.size() > 0) {
      final Application app = ApplicationManager.getApplication();
      final Runnable swingRunnable = new Runnable() {
        public void run() {
          myFailedModulePaths.clear();
          myFailedModulePaths.addAll(myModulePaths);
          final List<Module> modulesWithUnknownTypes = new ArrayList<Module>();
          List<ModuleLoadingErrorDescription> errors = new ArrayList<ModuleLoadingErrorDescription>();

          for (final ModulePath modulePath : myModulePaths) {
            try {
              final Module module = moduleModel.loadModuleInternal(modulePath.getPath());
              if (module.getModuleType() instanceof UnknownModuleType) {
                modulesWithUnknownTypes.add(module);
              }
              final String groupPathString = modulePath.getModuleGroup();
              if (groupPathString != null) {
                final String[] groupPath = groupPathString.split(MODULE_GROUP_SEPARATOR);
                moduleModel.setModuleGroupPath(module, groupPath); //model should be updated too
              }
              myFailedModulePaths.remove(modulePath);
            }
            catch (final IOException e) {
              errors.add(ModuleLoadingErrorDescription.create(ProjectBundle.message("module.cannot.load.error", modulePath.getPath(), e.getMessage()),
                                                           modulePath, ModuleManagerImpl.this));
            }
            catch (final ModuleWithNameAlreadyExists moduleWithNameAlreadyExists) {
              errors.add(ModuleLoadingErrorDescription.create(moduleWithNameAlreadyExists.getMessage(), modulePath, ModuleManagerImpl.this));
            }
            catch (StateStorage.StateStorageException e) {
              errors.add(ModuleLoadingErrorDescription.create(ProjectBundle.message("module.cannot.load.error", modulePath.getPath(), e.getMessage()),
                                                           modulePath, ModuleManagerImpl.this));
            }
          }

          fireErrors(errors);

          if (!app.isHeadlessEnvironment() && !modulesWithUnknownTypes.isEmpty()) {
            String message;
            if (modulesWithUnknownTypes.size() == 1) {
              message = ProjectBundle.message("module.unknown.type.single.error", modulesWithUnknownTypes.get(0).getName());
            }
            else {
              StringBuilder modulesBuilder = new StringBuilder();
              for (final Module module : modulesWithUnknownTypes) {
                modulesBuilder.append("\n\"");
                modulesBuilder.append(module.getName());
                modulesBuilder.append("\"");
              }
              message = ProjectBundle.message("module.unknown.type.multiple.error", modulesBuilder.toString());
            }
            // it is not modal warning at all
            //Messages.showWarningDialog(myProject, message, ProjectBundle.message("module.unknown.type.title"));
            Notifications.Bus.notify(
              new Notification(
                "Module Manager",
                ProjectBundle.message("module.unknown.type.title"),
                message,
                NotificationType.WARNING
              ), 
              NotificationDisplayType.STICKY_BALLOON,
              myProject
            );
          }
        }
      };
      if (app.isDispatchThread() || app.isHeadlessEnvironment()) {
        swingRunnable.run();
      }
      else {
        app.invokeAndWait(swingRunnable, ModalityState.defaultModalityState());
      }
    }
  }

  private void fireErrors(final List<ModuleLoadingErrorDescription> errors) {
    if (errors.isEmpty()) return;

    myModuleModel.myModulesCache = null;
    for (ModuleLoadingErrorDescription error : errors) {
      final Module module = myModuleModel.myPathToModule.remove(FileUtil.toSystemIndependentName(error.getModulePath().getPath()));
      if (module != null) {
        Disposer.dispose(module);
      }
    }

    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      throw new RuntimeException(errors.get(0).getDescription());
    }

    ProjectLoadingErrorsNotifier.getInstance(myProject).registerErrors(errors);
  }

  public void removeFailedModulePath(@NotNull ModulePath modulePath) {
    myFailedModulePaths.remove(modulePath);
  }

  @NotNull
  public ModifiableModuleModel getModifiableModel() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return new ModuleModelImpl(myModuleModel);
  }


  private abstract static class SaveItem {

    protected abstract String getModuleName();
    protected abstract String getGroupPathString();
    protected abstract String getModuleFilePath();

    public final void writeExternal(Element parentElement) {
      Element moduleElement = new Element(ELEMENT_MODULE);
      final String moduleFilePath = getModuleFilePath();
      final String url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, moduleFilePath);
      moduleElement.setAttribute(ATTRIBUTE_FILEURL, url);
      // [dsl] support for older builds
      moduleElement.setAttribute(ATTRIBUTE_FILEPATH, moduleFilePath);

      final String groupPath = getGroupPathString();
      if (groupPath != null) {
        moduleElement.setAttribute(ATTRIBUTE_GROUP, groupPath);
      }
      parentElement.addContent(moduleElement);
    }
  }

  private class ModuleSaveItem extends SaveItem{
    private final Module myModule;

    public ModuleSaveItem(Module module) {
      myModule = module;
    }

    protected String getModuleName() {
      return myModule.getName();
    }

    protected String getGroupPathString() {
      String[] groupPath = getModuleGroupPath(myModule);
      return groupPath != null ? StringUtil.join(groupPath, MODULE_GROUP_SEPARATOR) : null;
    }

    protected String getModuleFilePath() {
      return myModule.getModuleFilePath().replace(File.separatorChar, '/');
    }
  }

  private static class ModulePathSaveItem extends SaveItem{
    private final ModulePath myModulePath;
    private final String myFilePath;
    private final String myName;

    public ModulePathSaveItem(ModulePath modulePath) {
      myModulePath = modulePath;
      myFilePath = modulePath.getPath().replace(File.separatorChar, '/');

      final int slashIndex = myFilePath.lastIndexOf('/');
      final int startIndex = slashIndex >= 0 && slashIndex + 1 < myFilePath.length() ? slashIndex + 1 : 0;
      final int endIndex = myFilePath.endsWith(ModuleFileType.DOT_DEFAULT_EXTENSION)
                           ? myFilePath.length() - ModuleFileType.DOT_DEFAULT_EXTENSION.length()
                           : myFilePath.length();
      myName = myFilePath.substring(startIndex, endIndex);
    }

    protected String getModuleName() {
      return myName;
    }

    protected String getGroupPathString() {
      return myModulePath.getModuleGroup();
    }

    protected String getModuleFilePath() {
      return myFilePath;
    }
  }

  public void writeExternal(Element element) {
    final Element modules = new Element(ELEMENT_MODULES);
    final Module[] collection = getModules();

    ArrayList<SaveItem> sorted = new ArrayList<SaveItem>(collection.length + myFailedModulePaths.size());
    for (Module module : collection) {
      sorted.add(new ModuleSaveItem(module));
    }
    for (ModulePath modulePath : myFailedModulePaths) {
      sorted.add(new ModulePathSaveItem(modulePath));
    }
    Collections.sort(sorted, new Comparator<SaveItem>() {
      public int compare(SaveItem item1, SaveItem item2) {
        return item1.getModuleName().compareTo(item2.getModuleName());
      }
    });
    for (SaveItem saveItem : sorted) {
      saveItem.writeExternal(modules);
    }

    element.addContent(modules);
  }

  private void fireModuleAdded(Module module) {
    myMessageBus.syncPublisher(ProjectTopics.MODULES).moduleAdded(myProject, module);
  }

  private void fireModuleRemoved(Module module) {
    myMessageBus.syncPublisher(ProjectTopics.MODULES).moduleRemoved(myProject, module);
  }

  private void fireBeforeModuleRemoved(Module module) {
    myMessageBus.syncPublisher(ProjectTopics.MODULES).beforeModuleRemoved(myProject, module);
  }

  private final Map<ModuleListener, MessageBusConnection> myAdapters = new HashMap<ModuleListener, MessageBusConnection>();
  public void addModuleListener(@NotNull ModuleListener listener) {
    final MessageBusConnection connection = myMessageBus.connect();
    connection.subscribe(ProjectTopics.MODULES, listener);
    myAdapters.put(listener, connection);
  }

  public void addModuleListener(@NotNull ModuleListener listener, Disposable parentDisposable) {
    final MessageBusConnection connection = myMessageBus.connect(parentDisposable);
    connection.subscribe(ProjectTopics.MODULES, listener);
  }

  public void removeModuleListener(@NotNull ModuleListener listener) {
    final MessageBusConnection adapter = myAdapters.remove(listener);
    if (adapter != null) {
      adapter.disconnect();
    }
  }

  @NotNull
  public Module newModule(@NotNull String filePath, @NotNull ModuleType moduleType) {
    myModificationCount++;
    final ModifiableModuleModel modifiableModel = getModifiableModel();
    final Module module = modifiableModel.newModule(filePath, moduleType);
    modifiableModel.commit();
    return module;
  }

  @NotNull
  public Module loadModule(@NotNull String filePath) throws InvalidDataException,
                                                   IOException,
                                                   JDOMException,
                                                   ModuleWithNameAlreadyExists {
    myModificationCount++;
    final ModifiableModuleModel modifiableModel = getModifiableModel();
    final Module module = modifiableModel.loadModule(filePath);
    modifiableModel.commit();
    return module;
  }

  public void disposeModule(@NotNull final Module module) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final ModifiableModuleModel modifiableModel = getModifiableModel();
        modifiableModel.disposeModule(module);
        modifiableModel.commit();
      }
    });
  }

  @NotNull
  public Module[] getModules() {
    if (myModuleModel.myIsWritable) {
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    return myModuleModel.getModules();
  }

  private Module[] myCachedSortedModules = null;

  @NotNull
  public Module[] getSortedModules() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    myConnection.deliverImmediately();
    if (myCachedSortedModules == null) {
      myCachedSortedModules = myModuleModel.getSortedModules();
    }
    return myCachedSortedModules;
  }

  public Module findModuleByName(@NotNull String name) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return myModuleModel.findModuleByName(name);
  }

  private Comparator<Module> myCachedModuleComparator = null;

  @NotNull
  public Comparator<Module> moduleDependencyComparator() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    myConnection.deliverImmediately();
    if (myCachedModuleComparator == null) {
      myCachedModuleComparator = myModuleModel.moduleDependencyComparator();
    }
    return myCachedModuleComparator;
  }

  @NotNull
  public Graph<Module> moduleGraph() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return myModuleModel.moduleGraph();
  }

  @NotNull public List<Module> getModuleDependentModules(@NotNull Module module) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return myModuleModel.getModuleDependentModules(module);
  }

  public boolean isModuleDependent(@NotNull Module module, @NotNull Module onModule) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return myModuleModel.isModuleDependent(module, onModule);
  }

  public void projectOpened() {
    Runnable runnableWithProgress = new Runnable() {
      public void run() {
        for (final Module module : myModuleModel.myPathToModule.values()) {
          final Application app = ApplicationManager.getApplication();
          final Runnable swingRunnable = new Runnable() {
            public void run() {
              app.runWriteAction(new Runnable() {
                public void run() {
                  ((ModuleImpl)module).moduleAdded();
                  fireModuleAdded(module);
                }
              });
            }
          };
          if (app.isDispatchThread() || app.isHeadlessEnvironment()) {
            swingRunnable.run();
          }
          else {
            app.invokeAndWait(swingRunnable, ModalityState.defaultModalityState());
          }
        }
      }
    };

    ProgressManager.getInstance().runProcessWithProgressSynchronously(runnableWithProgress, "Loading modules", false, myProject);

    myModuleModel.projectOpened();
  }

  public void projectClosed() {
    myModuleModel.projectClosed();
  }

  public static void commitModelWithRunnable(ModifiableModuleModel model, Runnable runnable) {
    ((ModuleModelImpl)model).commitWithRunnable(runnable);
  }

  class ModuleModelImpl implements ModifiableModuleModel {
    private final Map<String, Module> myPathToModule = new LinkedHashMap<String, Module>();
    private Module[] myModulesCache;

    private final List<Module> myModulesToDispose = new ArrayList<Module>();
    private final Map<Module, String> myModuleToNewName = new HashMap<Module, String>();
    private final Map<String, Module> myNewNameToModule = new HashMap<String, Module>();
    private boolean myIsWritable;
    private Map<Module, String []> myModuleGroupPath;

    ModuleModelImpl() {
      myIsWritable = false;
    }

    ModuleModelImpl(ModuleModelImpl that) {
      myPathToModule.putAll(that.myPathToModule);
      final Map<Module, String[]> groupPath = that.myModuleGroupPath;
      if (groupPath != null){
        myModuleGroupPath = new THashMap<Module, String[]>();
        myModuleGroupPath.putAll(that.myModuleGroupPath);
      }
      myIsWritable = true;
    }

    private void assertWritable() {
      LOG.assertTrue(myIsWritable, "Attempt to modify committed ModifiableModuleModel");
    }

    @NotNull
    public Module[] getModules() {
      if (myModulesCache == null) {
        Collection<Module> modules = myPathToModule.values();
        myModulesCache = modules.toArray(new Module[modules.size()]);
      }
      return myModulesCache;
    }

    private Module[] getSortedModules() {
      Module[] allModules = getModules();
      Arrays.sort(allModules, moduleDependencyComparator());
      return allModules;
    }

    public void renameModule(@NotNull Module module, @NotNull String newName) throws ModuleWithNameAlreadyExists {
      final Module oldModule = getModuleByNewName(newName);
      myNewNameToModule.remove(myModuleToNewName.get(module));
      if(module.getName().equals(newName)){ // if renaming to itself, forget it altogether
        myModuleToNewName.remove(module);
        myNewNameToModule.remove(newName);
      } else {
        myModuleToNewName.put(module, newName);
        myNewNameToModule.put(newName, module);
      }

      if (oldModule != null) {
        throw new ModuleWithNameAlreadyExists(ProjectBundle.message("module.already.exists.error", newName), newName);
      }
    }

    public Module getModuleToBeRenamed(@NotNull String newName) {
      return myNewNameToModule.get(newName);
    }

    public Module getModuleByNewName(String newName) {
      final Module moduleToBeRenamed = getModuleToBeRenamed(newName);
      if (moduleToBeRenamed != null) {
        return moduleToBeRenamed;
      }
      final Module moduleWithOldName = findModuleByName(newName);
      if (myModuleToNewName.get(moduleWithOldName) == null) {
        return moduleWithOldName;
      }
      else {
        return null;
      }
    }

    public String getNewName(@NotNull Module module) {
      return myModuleToNewName.get(module);
    }

    @NotNull
    public Module newModule(@NotNull String filePath, @NotNull ModuleType moduleType) {
      return newModule(filePath,moduleType,null);
    }

    @NotNull
    public Module newModule(@NotNull String filePath, @NotNull ModuleType moduleType, @Nullable Map<String, String> options) {
      assertWritable();
      filePath = resolveShortWindowsName(filePath);

      ModuleImpl module = getModuleByFilePath(filePath);
      if (module == null) {
        module = new ModuleImpl(filePath, myProject);
        module.setModuleType(moduleType);
        if (options != null) {
          for ( Map.Entry<String,String> option : options.entrySet()) {
            module.setOption(option.getKey(),option.getValue());
          }
        }
        module.loadModuleComponents();
        initModule(module);
      }
      return module;
    }

    private String resolveShortWindowsName(String filePath) {
      try {
        filePath = FileUtil.resolveShortWindowsName(filePath);
      }
      catch (IOException ignored) {
      }
      return filePath;
    }

    private ModuleImpl getModuleByFilePath(String filePath) {
      final Collection<Module> modules = myPathToModule.values();
      for (Module module : modules) {
        if (filePath.equals(module.getModuleFilePath())) {
          return (ModuleImpl)module;
        }
      }
      return null;
    }

    @NotNull
    public Module loadModule(@NotNull String filePath) throws InvalidDataException,
                                                     IOException,
                                                     ModuleWithNameAlreadyExists {
      assertWritable();
      try {
        return loadModuleInternal(filePath);
      }
      catch (StateStorage.StateStorageException e) {
        throw new IOException(ProjectBundle.message("module.corrupted.file.error", FileUtil.toSystemDependentName(filePath), e.getMessage()));
      }
    }

    private Module loadModuleInternal(String filePath) throws ModuleWithNameAlreadyExists,
                                                              IOException, StateStorage.StateStorageException {
      final File moduleFile = new File(filePath);
      filePath = resolveShortWindowsName(filePath);

      final String name = moduleFile.getName();
      if (name.endsWith(ModuleFileType.DOT_DEFAULT_EXTENSION)) {
        final String moduleName = name.substring(0, name.length() - 4);
        for (Module module : myPathToModule.values()) {
          if (module.getName().equals(moduleName)) {
            throw new ModuleWithNameAlreadyExists(ProjectBundle.message("module.already.exists.error", moduleName), moduleName);
          }
        }
      }
      if (!moduleFile.exists()) {
        throw new IOException(ProjectBundle.message("module.file.does.not.exist.error", moduleFile.getPath()));
      }
      ModuleImpl module = getModuleByFilePath(filePath);
      if (module == null) {
        module = new ModuleImpl(filePath, myProject);
        module.getStateStore().load();
        module.loadModuleComponents();
        initModule(module);
      }
      return module;
    }

    private void initModule(ModuleImpl module) {
      String path = module.getModuleFilePath();
      myModulesCache = null;
      myPathToModule.put(path, module);
      module.init();
    }

    public void disposeModule(@NotNull Module module) {
      assertWritable();
      myModulesCache = null;
      if (myPathToModule.values().contains(module)) {
        myPathToModule.remove(module.getModuleFilePath());
        myModulesToDispose.add(module);
      }
      if (myModuleGroupPath != null){
        myModuleGroupPath.remove(module);
      }
    }

    public Module findModuleByName(@NotNull String name) {
      for (Module module : myPathToModule.values()) {
        if (!module.isDisposed() && module.getName().equals(name)) {
          return module;
        }
      }
      return null;
    }

    private Comparator<Module> moduleDependencyComparator() {
      DFSTBuilder<Module> builder = new DFSTBuilder<Module>(moduleGraph());
      return builder.comparator();
    }

    private Graph<Module> moduleGraph() {
      return GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<Module>() {
        public Collection<Module> getNodes() {
          return myPathToModule.values();
        }

        public Iterator<Module> getIn(Module m) {
          Module[] dependentModules = ModuleRootManager.getInstance(m).getDependencies();
          return Arrays.asList(dependentModules).iterator();
        }
      }));
    }

    @NotNull private List<Module> getModuleDependentModules(Module module) {
      List<Module> result = new ArrayList<Module>();
      for (Module aModule : myPathToModule.values()) {
        if (isModuleDependent(aModule, module)) {
          result.add(aModule);
        }
      }
      return result;
    }

    private boolean isModuleDependent(Module module, Module onModule) {
      return ModuleRootManager.getInstance(module).isDependsOn(onModule);
    }

    public void commit() {
      ProjectRootManagerEx.getInstanceEx(myProject).multiCommit(this, new ModifiableRootModel[0]);
    }

    public void commitWithRunnable(Runnable runnable) {
      commitModel(this, runnable);
      clearRenamingStuff();
    }

    private void clearRenamingStuff() {
      myModuleToNewName.clear();
      myNewNameToModule.clear();
    }

    public void dispose() {
      assertWritable();
      ApplicationManager.getApplication().assertWriteAccessAllowed();
      final Collection<Module> list = myModuleModel.myPathToModule.values();
      final Collection<Module> thisModules = myPathToModule.values();
      for (Module thisModule1 : thisModules) {
        ModuleImpl thisModule = (ModuleImpl)thisModule1;
        if (!list.contains(thisModule)) {
          Disposer.dispose(thisModule);
        }
      }
      for (Module moduleToDispose : myModulesToDispose) {
        if (!list.contains(moduleToDispose)) {
          Disposer.dispose(moduleToDispose);
        }
      }
      clearRenamingStuff();
    }

    public boolean isChanged() {
      if (!myIsWritable) {
        return false;
      }
      Set<Module> thisModules = new HashSet<Module>(myPathToModule.values());
      Set<Module> thatModules = new HashSet<Module>(myModuleModel.myPathToModule.values());
      return !thisModules.equals(thatModules) || !Comparing.equal(myModuleModel.myModuleGroupPath, myModuleGroupPath);
    }

    private void disposeModel() {
      myModulesCache = null;
      for (final Module module : myPathToModule.values()) {
        Disposer.dispose(module);
      }
      myPathToModule.clear();
      myModuleGroupPath = null;
    }

    public void projectOpened() {
      final Collection<Module> collection = myPathToModule.values();
      for (final Module aCollection : collection) {
        ModuleImpl module = (ModuleImpl)aCollection;
        module.projectOpened();
      }
    }

    public void projectClosed() {
      final Collection<Module> collection = myPathToModule.values();
      for (final Module aCollection : collection) {
        ModuleImpl module = (ModuleImpl)aCollection;
        module.projectClosed();
      }
    }

    public String[] getModuleGroupPath(Module module) {
      return myModuleGroupPath == null ? null : myModuleGroupPath.get(module);
    }

    public boolean hasModuleGroups() {
      return myModuleGroupPath != null && !myModuleGroupPath.isEmpty();
    }

    public void setModuleGroupPath(Module module, String[] groupPath) {
      if (myModuleGroupPath == null) {
        myModuleGroupPath = new THashMap<Module, String[]>();
      }
      if (groupPath == null) {
        myModuleGroupPath.remove(module);
      }
      else {
        myModuleGroupPath.put(module, groupPath);
      }
    }
  }

  private void commitModel(final ModuleModelImpl moduleModel, final Runnable runnable) {
    myModuleModel.myModulesCache = null;
    myModificationCount++;
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    final Collection<Module> oldModules = myModuleModel.myPathToModule.values();
    final Collection<Module> newModules = moduleModel.myPathToModule.values();
    final List<Module> removedModules = new ArrayList<Module>(oldModules);
    removedModules.removeAll(newModules);
    final List<Module> addedModules = new ArrayList<Module>(newModules);
    addedModules.removeAll(oldModules);

    ProjectRootManagerEx.getInstanceEx(myProject).makeRootsChange(new Runnable() {
      public void run() {
        for (Module removedModule : removedModules) {
          fireBeforeModuleRemoved(removedModule);
          cleanCachedStuff();
        }

        List<Module> neverAddedModules = new ArrayList<Module>(moduleModel.myModulesToDispose);
        neverAddedModules.removeAll(myModuleModel.myPathToModule.values());
        for (final Module neverAddedModule : neverAddedModules) {
          ModuleImpl module = (ModuleImpl)neverAddedModule;
          module.putUserData(DISPOSED_MODULE_NAME, module.getName());
          Disposer.dispose(module);
        }

        if (runnable != null) {
          runnable.run();
        }

        final Map<Module, String> modulesToNewNamesMap = moduleModel.myModuleToNewName;
        final Set<Module> modulesToBeRenamed = modulesToNewNamesMap.keySet();
        modulesToBeRenamed.removeAll(moduleModel.myModulesToDispose);
        final List<Module> modules = new ArrayList<Module>();
        for (final Module aModulesToBeRenamed : modulesToBeRenamed) {
          ModuleImpl module = (ModuleImpl)aModulesToBeRenamed;
          moduleModel.myPathToModule.remove(module.getModuleFilePath());
          modules.add(module);
          module.rename(modulesToNewNamesMap.get(module));
          moduleModel.myPathToModule.put(module.getModuleFilePath(), module);
        }

        moduleModel.myIsWritable = false;
        myModuleModel = moduleModel;

        for (Module module : removedModules) {
          fireModuleRemoved(module);
          cleanCachedStuff();
          Disposer.dispose(module);
          cleanCachedStuff();
        }

        for (Module addedModule : addedModules) {
          ((ModuleImpl)addedModule).moduleAdded();
          cleanCachedStuff();
          fireModuleAdded(addedModule);
          cleanCachedStuff();
        }
        cleanCachedStuff();
        fireModulesRenamed(modules);
        cleanCachedStuff();
      }
    }, false, true);
  }

  private void fireModulesRenamed(List<Module> modules) {
    if (!modules.isEmpty()) {
      myMessageBus.syncPublisher(ProjectTopics.MODULES).modulesRenamed(myProject, modules);
    }
  }

  void fireModuleRenamedByVfsEvent(@NotNull final Module module) {
    ProjectRootManagerEx.getInstanceEx(myProject).makeRootsChange(new Runnable() {
      public void run() {
        fireModulesRenamed(Collections.singletonList(module));
      }
    }, false, true);
  }

  public String[] getModuleGroupPath(@NotNull Module module) {
    return myModuleModel.getModuleGroupPath(module);
  }

  public void setModuleGroupPath(Module module, String[] groupPath) {
    myModuleModel.setModuleGroupPath(module, groupPath);
  }
}

