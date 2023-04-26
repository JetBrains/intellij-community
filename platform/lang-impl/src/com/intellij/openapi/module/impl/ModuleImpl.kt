// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl;

import com.intellij.configurationStore.RenameableStateStorageManager;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.plugins.ContainerDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.components.impl.stores.ModuleStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.scopes.ModuleScopeProviderImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.roots.ExternalProjectSystemRegistry;
import com.intellij.openapi.roots.ProjectModelElement;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.serviceContainer.ComponentManagerImpl;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Property;
import kotlin.Unit;
import kotlin.coroutines.EmptyCoroutineContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ModuleImpl extends ComponentManagerImpl implements ModuleEx, Queryable {
  private static final Logger LOG = Logger.getInstance(ModuleImpl.class);

  private final @NotNull Project project;
  protected @Nullable VirtualFilePointer imlFilePointer;
  private volatile boolean isModuleAdded;

  private String name;

  private final ModuleScopeProvider moduleScopeProvider;

  @ApiStatus.Internal
  public ModuleImpl(@NotNull String name, @NotNull Project project, @NotNull String filePath) {
    this(name, project);
    imlFilePointer = VirtualFilePointerManager.getInstance().create(
      VfsUtilCore.pathToUrl(filePath), this,
      new VirtualFilePointerListener() {
        @Override
        public void validityChanged(@NotNull VirtualFilePointer @NotNull [] pointers) {
          if (imlFilePointer == null) return;
          VirtualFile virtualFile = imlFilePointer.getFile();
          if (virtualFile != null) {
            ((ModuleStore)getStore()).setPath(virtualFile.toNioPath(), virtualFile, false);
            ModuleManager.getInstance(ModuleImpl.this.project).incModificationCount();
          }
        }
      });
  }

  @ApiStatus.Internal
  public ModuleImpl(@NotNull String name, @NotNull Project project, @Nullable VirtualFilePointer virtualFilePointer) {
    this(name, project);

    imlFilePointer = virtualFilePointer;
  }

  @ApiStatus.Internal
  public ModuleImpl(@NotNull String name, @NotNull Project project) {
    super((ComponentManagerImpl)project, false, EmptyCoroutineContext.INSTANCE);

    registerServiceInstance(Module.class, this, ComponentManagerImpl.fakeCorePluginDescriptor);
    this.project = project;
    moduleScopeProvider = new ModuleScopeProviderImpl(this);
    this.name = name;
  }

  @Override
  public void init(@Nullable Runnable beforeComponentCreation) {
    // do not measure (activityNamePrefix method not overridden by this class)
    // because there are a lot of modules and no need to measure each one
    registerComponents();
    if (!isPersistent()) {
      registerService(IComponentStore.class,
                      NonPersistentModuleStore.class,
                      ComponentManagerImpl.fakeCorePluginDescriptor,
                      true);
    }
    if (beforeComponentCreation != null) {
      beforeComponentCreation.run();
    }
    createComponents();
  }

  private boolean isPersistent() {
    return imlFilePointer != null;
  }

  @Override
  public final boolean isDisposed() {
    // in case of light project in tests when it's temporarily disposed, the module should be treated as disposed too.
    //noinspection TestOnlyProblems
    return super.isDisposed() || ((ProjectEx)project).isLight() && project.isDisposed();
  }

  @Override
  protected boolean isComponentSuitable(@NotNull ComponentConfig componentConfig) {
    if (!super.isComponentSuitable(componentConfig)) {
      return false;
    }

    Map<String, String> options = componentConfig.options;
    if (options == null || options.isEmpty()) {
      return true;
    }

    for (String optionName : options.keySet()) {
      if ("workspace".equals(optionName) || "overrides".equals(optionName)) {
        continue;
      }

      // we cannot filter using module options because at this moment module file data could be not loaded
      String message = "Don't specify " + optionName + " in the component registration, transform component to service and implement your logic in your getInstance() method";
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.error(message);
      }
      else {
        LOG.warn(message);
      }
    }

    return true;
  }

  @Override
  public @Nullable VirtualFile getModuleFile() {
    if (imlFilePointer == null) {
      return null;
    }
    return imlFilePointer.getFile();
  }

  @Override
  public void rename(@NotNull String newName, boolean notifyStorage) {
    name = newName;
    if (notifyStorage) {
      ((RenameableStateStorageManager)getStore().getStorageManager()).rename(newName + ModuleFileType.DOT_DEFAULT_EXTENSION);
    }
  }

  protected @NotNull IComponentStore getStore() {
    return Objects.requireNonNull(getService(IComponentStore.class));
  }

  @Override
  public boolean canStoreSettings() {
    return !(getStore() instanceof NonPersistentModuleStore);
  }

  @Override
  public @NotNull Path getModuleNioFile() {
    if (!isPersistent()) {
      return Path.of("");
    }
    return getStore().getStorageManager().expandMacro(StoragePathMacros.MODULE_FILE);
  }

  @Override
  public synchronized void dispose() {
    isModuleAdded = false;
    super.dispose();
  }

  @Override
  protected @NotNull ContainerDescriptor getContainerDescriptor(@NotNull IdeaPluginDescriptorImpl pluginDescriptor) {
    return pluginDescriptor.moduleContainerDescriptor;
  }

  @Override
  public void projectClosed() {
    @SuppressWarnings({"removal", "UnnecessaryFullyQualifiedName"})
    List<com.intellij.openapi.module.ModuleComponent> components =
      collectInitializedComponents(com.intellij.openapi.module.ModuleComponent.class);
    for (int i = components.size() - 1; i >= 0; i--) {
      try {
        //noinspection removal
        components.get(i).projectClosed();
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public @NotNull Project getProject() {
    return project;
  }

  @Override
  public @NotNull String getName() {
    return name;
  }

  @Override
  public boolean isLoaded() {
    return isModuleAdded;
  }

  @Override
  public void moduleAdded(List<? super ModuleComponent> oldComponents) {
    isModuleAdded = true;
    //noinspection removal,UnnecessaryFullyQualifiedName
    processInitializedComponents(com.intellij.openapi.module.ModuleComponent.class, (component) -> {
      oldComponents.add(component);
      return Unit.INSTANCE;
    });
  }

  @Override
  public void setOption(@NotNull String key, @Nullable String value) {
    DeprecatedModuleOptionManager manager = getOptionManager();
    if (value == null) {
      if (manager.state.options.remove(key) != null) {
        manager.incModificationCount();
      }
    }
    else if (!value.equals(manager.state.options.put(key, value))) {
      manager.incModificationCount();
    }
  }

  private @NotNull DeprecatedModuleOptionManager getOptionManager() {
    //noinspection ConstantConditions
    return ((Module)this).getService(DeprecatedModuleOptionManager.class);
  }

  @Override
  public String getOptionValue(@NotNull String key) {
    return getOptionManager().state.options.get(key);
  }

  @Override
  public @NotNull GlobalSearchScope getModuleScope() {
    return moduleScopeProvider.getModuleScope();
  }

  @Override
  public @NotNull GlobalSearchScope getModuleScope(boolean includeTests) {
    return moduleScopeProvider.getModuleScope(includeTests);
  }

  @Override
  public @NotNull GlobalSearchScope getModuleWithLibrariesScope() {
    return moduleScopeProvider.getModuleWithLibrariesScope();
  }

  @Override
  public @NotNull GlobalSearchScope getModuleWithDependenciesScope() {
    return moduleScopeProvider.getModuleWithDependenciesScope();
  }

  @Override
  public @NotNull GlobalSearchScope getModuleContentScope() {
    return moduleScopeProvider.getModuleContentScope();
  }

  @Override
  public @NotNull GlobalSearchScope getModuleContentWithDependenciesScope() {
    return moduleScopeProvider.getModuleContentWithDependenciesScope();
  }

  @Override
  public @NotNull GlobalSearchScope getModuleWithDependenciesAndLibrariesScope(boolean includeTests) {
    return moduleScopeProvider.getModuleWithDependenciesAndLibrariesScope(includeTests);
  }

  @Override
  public @NotNull GlobalSearchScope getModuleWithDependentsScope() {
    return moduleScopeProvider.getModuleWithDependentsScope();
  }

  @Override
  public @NotNull GlobalSearchScope getModuleTestsWithDependentsScope() {
    return moduleScopeProvider.getModuleTestsWithDependentsScope();
  }

  @Override
  public @NotNull GlobalSearchScope getModuleRuntimeScope(boolean includeTests) {
    return moduleScopeProvider.getModuleRuntimeScope(includeTests);
  }

  @Override
  public @NotNull GlobalSearchScope getModuleProductionSourceScope() {
    return moduleScopeProvider.getModuleProductionSourceScope();
  }

  @Override
  public @NotNull GlobalSearchScope getModuleTestSourceScope() {
    return moduleScopeProvider.getModuleTestSourceScope();
  }

  @Override
  public void clearScopesCache() {
    moduleScopeProvider.clearCache();
  }

  @Override
  public String toString() {
    if (name == null) {
      return "Module (not initialized)";
    }
    return "Module: '" + getName() + "'" + (isDisposed() ? " (disposed)" : "");
  }

  @Override
  public void putInfo(@NotNull Map<? super String, ? super String> info) {
    info.put("id", "Module");
    info.put("name", getName());
  }

  @ApiStatus.Internal
  @State(name = "DeprecatedModuleOptionManager", useLoadedStateAsExisting = false /* doesn't make sense to check it */)
  public static class DeprecatedModuleOptionManager extends SimpleModificationTracker implements PersistentStateComponent<DeprecatedModuleOptionManager.State>,
                                                                                          ProjectModelElement {
    private final Module module;

    DeprecatedModuleOptionManager(@NotNull Module module) {
      this.module = module;
    }

    @Override
    public @Nullable ProjectModelExternalSource getExternalSource() {
      if (state.options.size() > 1 || state.options.size() == 1 && !state.options.containsKey(Module.ELEMENT_TYPE) /* unrealistic case, but just to be sure */) {
        return null;
      }
      return ExternalProjectSystemRegistry.getInstance().getExternalSource(module);
    }

    static final class State {
      @Property(surroundWithTag = false)
      @MapAnnotation(surroundKeyWithTag = false, surroundValueWithTag = false, surroundWithTag = false, entryTagName = "option")
      public final Map<String, String> options = new HashMap<>();
    }

    private State state = new State();

    @Override
    public @Nullable State getState() {
      return state;
    }

    @Override
    public void loadState(@NotNull State state) {
      this.state = state;
    }
  }
}
