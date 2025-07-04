// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.module.impl.ModuleConfigurationStateImpl;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.impl.ModuleConfigurationEditorProviderEp;
import com.intellij.platform.workspace.storage.MutableEntityStorage;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public abstract class ModuleEditor implements Place.Navigator, Disposable {
  private static final ExtensionPointName<ModuleConfigurationEditorProviderEp> EP_NAME =
    new ExtensionPointName<>("com.intellij.moduleConfigurationEditorProvider");

  public static final String SELECTED_EDITOR_NAME = "selectedEditor";

  private final Project myProject;
  private JPanel myGenericSettingsPanel;
  private ModificationOfImportedModelWarningComponent myModificationOfImportedModelWarningComponent;
  private ModifiableRootModel myModifiableRootModel; // important: in order to correctly update OrderEntries UI use corresponding proxy for the model

  private final ModulesConfigurator myModulesProvider;
  private String myName;
  private final Module myModule;

  protected final List<ModuleConfigurationEditor> myEditors = new ArrayList<>();
  private ModifiableRootModel myModifiableRootModelProxy;

  private final EventDispatcher<ChangeListener> myEventDispatcher = EventDispatcher.create(ChangeListener.class);
  private static final @NonNls String METHOD_COMMIT = "commit";
  private boolean myEditorsInitialized;

  protected History myHistory;

  public ModuleEditor(@NotNull Project project, @NotNull ModulesConfigurator modulesProvider, @NotNull Module module) {
    myProject = project;
    myModulesProvider = modulesProvider;
    myModule = module;
    myName = module.getName();
  }

  public void init(History history) {
    myHistory = history;
    restoreSelectedEditor();
  }

  public abstract ProjectFacetsConfigurator getFacetsConfigurator();

  protected abstract JComponent createCenterPanel();

  public abstract @Nullable ModuleConfigurationEditor getSelectedEditor();

  public abstract void selectEditor(String displayName);

  protected abstract void restoreSelectedEditor();

  public abstract @Nullable ModuleConfigurationEditor getEditor(@NotNull String displayName);

  protected abstract void disposeCenterPanel();

  public interface ChangeListener extends EventListener {
    void moduleStateChanged(ModifiableRootModel moduleRootModel);
  }

  /**
   * Listeners are automatically unsubscribed on dispose
   */
  public void addChangeListener(ChangeListener listener) {
    myEventDispatcher.addListener(listener);
  }

  public @Nullable Module getModule() {
    return myModulesProvider.getModule(myName);
  }

  public ModifiableRootModel getModifiableRootModel() {
    if (myModifiableRootModel == null) {
      final Module module = getModule();
      if (module != null) {
        MutableEntityStorage builder = myModulesProvider.getWorkspaceEntityStorageBuilder();
        myModifiableRootModel = ModuleRootManagerEx.getInstanceEx(module).getModifiableModelForMultiCommit(new UIRootConfigurationAccessor(myProject, builder));
      }
    }
    return myModifiableRootModel;
  }

  public OrderEntry @NotNull [] getOrderEntries() {
    if (myModifiableRootModel == null) { // do not clone all model if not necessary
      return ModuleRootManager.getInstance(getModule()).getOrderEntries();
    }
    return myModifiableRootModel.getOrderEntries();
  }

  public ModifiableRootModel getModifiableRootModelProxy() {
    if (myModifiableRootModelProxy == null) {
      final ModifiableRootModel rootModel = getModifiableRootModel();
      if (rootModel != null) {
        myModifiableRootModelProxy = ReflectionUtil.proxy(
          getClass().getClassLoader(), ModifiableRootModel.class, new ModifiableRootModelInvocationHandler(rootModel)
        );
      }
    }
    return myModifiableRootModelProxy;
  }

  public ModuleRootModel getRootModel() {
    if (myModifiableRootModel != null) {
      return getModifiableRootModelProxy();
    }
    return ModuleRootManager.getInstance(myModule);
  }

  public boolean isModified() {
    if (!myModule.getName().equals(myName)) {
      return true;
    }
    for (ModuleConfigurationEditor moduleElementsEditor : myEditors) {
      if (moduleElementsEditor.isModified()) {
        return true;
      }
    }
    return false;
  }

  private void createEditors(@NotNull Module module) {
    ModuleConfigurationState state = createModuleConfigurationState();
    for (ModuleConfigurationEditorProviderEp providerEp : EP_NAME.getExtensionList()) {
      ModuleConfigurationEditorProvider provider = providerEp.getOrCreateInstance(module);
      ModuleConfigurationEditor[] editors = provider.createEditors(state);
      if (editors.length > 0 && provider instanceof ModuleConfigurationEditorProviderEx &&
          ((ModuleConfigurationEditorProviderEx)provider).isCompleteEditorSet()) {
        myEditors.clear();
        ContainerUtil.addAll(myEditors, editors);
        break;
      }
      else {
        ContainerUtil.addAll(myEditors, editors);
      }
    }

    for (ModuleConfigurationEditor editor : myEditors) {
      if (editor instanceof ModuleElementsEditor) {
        ((ModuleElementsEditor)editor).addListener(this::updateImportedModelWarning);
      }
    }
  }

  public @NotNull ModuleConfigurationState createModuleConfigurationState() {
    return new ModuleConfigurationStateImpl(myProject, myModulesProvider) {
      @Override
      public ModifiableRootModel getModifiableRootModel() {
        return getModifiableRootModelProxy();
      }

      @Override
      public ModuleRootModel getCurrentRootModel() {
        return ModuleEditor.this.getRootModel();
      }

      @Override
      public FacetsProvider getFacetsProvider() {
        return getFacetsConfigurator();
      }
    };
  }

  private @NotNull JPanel createPanel() {
    // initialize model if needed
    getModifiableRootModel();
    getModifiableRootModelProxy();

    myGenericSettingsPanel = new ModuleEditorPanel();

    Module module = getModule();
    if (module  != null) {
      createEditors(module);
    }

    final JComponent component = createCenterPanel();
    myGenericSettingsPanel.add(component, BorderLayout.CENTER);
    myModificationOfImportedModelWarningComponent = new ModificationOfImportedModelWarningComponent();
    myGenericSettingsPanel.add(myModificationOfImportedModelWarningComponent.getLabel(), BorderLayout.SOUTH);
    updateImportedModelWarning();
    myEditorsInitialized = true;
    return myGenericSettingsPanel;
  }

  public @NotNull JPanel getPanel() {
    if (myGenericSettingsPanel == null) {
      myGenericSettingsPanel = createPanel();
    }

    return myGenericSettingsPanel;
  }

  public void moduleCountChanged() {
    updateOrderEntriesInEditors(false);
  }

  private void updateOrderEntriesInEditors(boolean forceInitEditors) {
    if (getModule() != null) { //module with attached module libraries was deleted
      if (myEditorsInitialized || forceInitEditors) {
        getPanel();  //init editor if needed
        for (final ModuleConfigurationEditor myEditor : myEditors) {
          myEditor.moduleStateChanged();
        }
        updateImportedModelWarning();
      }
      myEventDispatcher.getMulticaster().moduleStateChanged(getModifiableRootModelProxy());
    }
  }

  private void updateImportedModelWarning() {
    if (!myEditorsInitialized) return;

    ProjectModelExternalSource externalSource = ModuleRootManager.getInstance(myModule).getExternalSource();
    if (externalSource != null && isModified()) {
      myModificationOfImportedModelWarningComponent.showWarning(
        JavaUiBundle.message("project.roots.module.banner.text", myModule.getName()), externalSource);
    }
    else {
      myModificationOfImportedModelWarningComponent.hideWarning();
    }
  }

  public void updateCompilerOutputPathChanged(String baseUrl, String moduleName){
    if (myGenericSettingsPanel == null) return; //wasn't initialized yet
    for (final ModuleConfigurationEditor myEditor : myEditors) {
      if (myEditor instanceof ModuleElementsEditor) {
        ((ModuleElementsEditor)myEditor).moduleCompileOutputChanged(baseUrl, moduleName);
      }
    }
  }

  @Override
  public void dispose() {
    try {
      myEventDispatcher.getListeners().clear();
      for (final ModuleConfigurationEditor myEditor : myEditors) {
        myEditor.disposeUIResources();
      }

      myEditors.clear();

      disposeCenterPanel();

      if (myModifiableRootModel != null) {
        myModifiableRootModel.dispose();
      }

      myGenericSettingsPanel = null;
    }
    finally {
      resetModifiableModel();
    }
  }

  public ModifiableRootModel apply() throws ConfigurationException {
    for (ModuleConfigurationEditor editor : myEditors) {
      editor.saveData();
      editor.apply();
    }
    return myModifiableRootModel;
  }

  void resetModifiableModel() {
    myModifiableRootModel = null;
    myModifiableRootModelProxy = null;
  }

  public void canApply() throws ConfigurationException {
    for (ModuleConfigurationEditor editor : myEditors) {
      if (editor instanceof ModuleElementsEditor) {
        ((ModuleElementsEditor)editor).canApply();
      }
    }
  }

  public @NotNull String getName() {
    return myName;
  }

  private class ModifiableRootModelInvocationHandler implements InvocationHandler, ProxyDelegateAccessor {
    private final ModifiableRootModel myDelegateModel;
    private static final @NonNls Set<String> myCheckedNames = Set.of(
      "addOrderEntry", "addLibraryEntry", "addInvalidLibrary", "addModuleOrderEntry", "addInvalidModuleEntry", "removeOrderEntry",
      "setSdk", "inheritSdk", "inheritCompilerOutputPath", "setExcludeOutput", "replaceEntryOfType", "rearrangeOrderEntries");

    ModifiableRootModelInvocationHandler(@NotNull ModifiableRootModel model) {
      myDelegateModel = model;
    }

    @Override
    public Object invoke(Object object, Method method, Object[] params) throws Throwable {
      final boolean needUpdate = myCheckedNames.contains(method.getName());
      try {
        final Object result = method.invoke(myDelegateModel, unwrapParams(params));
        if (result instanceof LibraryTable table) {
          return ReflectionUtil.proxy(getClass().getClassLoader(), LibraryTable.class,
                                      new LibraryTableInvocationHandler(table));
        }
        return result;
      }
      catch (InvocationTargetException e) {
        throw e.getCause();
      }
      finally {
        if (needUpdate) {
          updateOrderEntriesInEditors(true);
        }
      }
    }

    @Override
    public Object getDelegate() {
      return myDelegateModel;
    }
  }

  private class LibraryTableInvocationHandler implements InvocationHandler, ProxyDelegateAccessor {
    private final LibraryTable myDelegateTable;
    private final @NonNls Set<String> myCheckedNames = new HashSet<>(Collections.singletonList("removeLibrary" /*,"createLibrary"*/));

    LibraryTableInvocationHandler(@NotNull LibraryTable table) {
      myDelegateTable = table;
    }

    @Override
    public Object invoke(Object object, Method method, Object[] params) throws Throwable {
      final boolean needUpdate = myCheckedNames.contains(method.getName());
      try {
        final Object result = method.invoke(myDelegateTable, unwrapParams(params));
        if (result instanceof Library library) {
          return ReflectionUtil.proxy(getClass().getClassLoader(), result instanceof LibraryEx ? LibraryEx.class : Library.class,
                                      new LibraryInvocationHandler(library));
        }
        if (result instanceof LibraryTable.ModifiableModel model) {
          return ReflectionUtil.proxy(getClass().getClassLoader(), LibraryTable.ModifiableModel.class,
                                      new LibraryTableModelInvocationHandler(model));
        }
        if (result instanceof Library[] libraries) {
          for (int idx = 0; idx < libraries.length; idx++) {
            Library library = libraries[idx];
            libraries[idx] =
              ReflectionUtil.proxy(getClass().getClassLoader(), library instanceof LibraryEx ? LibraryEx.class : Library.class,
                                   new LibraryInvocationHandler(library));
          }
        }
        return result;
      }
      catch (InvocationTargetException e) {
        throw e.getCause();
      }
      finally {
        if (needUpdate) {
          updateOrderEntriesInEditors(true);
        }
      }
    }

    @Override
    public Object getDelegate() {
      return myDelegateTable;
    }
  }

  private class LibraryInvocationHandler implements InvocationHandler, ProxyDelegateAccessor {
    private final Library myDelegateLibrary;

    LibraryInvocationHandler(@NotNull Library delegateLibrary) {
      myDelegateLibrary = delegateLibrary;
    }

    @Override
    public Object invoke(Object object, Method method, Object[] params) throws Throwable {
      try {
        final Object result = method.invoke(myDelegateLibrary, unwrapParams(params));
        if (result instanceof LibraryEx.ModifiableModelEx model) {
          return ReflectionUtil.proxy(getClass().getClassLoader(), LibraryEx.ModifiableModelEx.class,
                                      new LibraryModifiableModelInvocationHandler(model));
        }
        return result;
      }
      catch (InvocationTargetException e) {
        throw e.getCause();
      }
    }

    @Override
    public Object getDelegate() {
      return myDelegateLibrary;
    }
  }

  private class LibraryModifiableModelInvocationHandler implements InvocationHandler, ProxyDelegateAccessor {
    private final Library.ModifiableModel myDelegateModel;

    LibraryModifiableModelInvocationHandler(@NotNull Library.ModifiableModel delegateModel) {
      myDelegateModel = delegateModel;
    }

    @Override
    public Object invoke(Object object, Method method, Object[] params) throws Throwable {
      final boolean needUpdate = METHOD_COMMIT.equals(method.getName());
      try {
        return method.invoke(myDelegateModel, unwrapParams(params));
      }
      catch (InvocationTargetException e) {
        throw e.getCause();
      }
      finally {
        if (needUpdate) {
          updateOrderEntriesInEditors(true);
        }
      }
    }

    @Override
    public Object getDelegate() {
      return myDelegateModel;
    }
  }

  private class LibraryTableModelInvocationHandler implements InvocationHandler, ProxyDelegateAccessor {
    private final LibraryTable.ModifiableModel myDelegateModel;

    LibraryTableModelInvocationHandler(@NotNull LibraryTable.ModifiableModel delegateModel) {
      myDelegateModel = delegateModel;
    }

    @Override
    public Object invoke(Object object, Method method, Object[] params) throws Throwable {
      final boolean needUpdate = METHOD_COMMIT.equals(method.getName());
      try {
        Object result = method.invoke(myDelegateModel, unwrapParams(params));
        if (result instanceof Library[] libraries) {
          for (int idx = 0; idx < libraries.length; idx++) {
            Library library = libraries[idx];
            libraries[idx] = ReflectionUtil.proxy(getClass().getClassLoader(), LibraryEx.class, new LibraryInvocationHandler(library));
          }
        }
        if (result instanceof Library library) {
          result = ReflectionUtil.proxy(getClass().getClassLoader(), LibraryEx.class, new LibraryInvocationHandler(library));
        }
        return result;
      }
      catch (InvocationTargetException e) {
        throw e.getCause();
      }
      finally {
        if (needUpdate) {
          updateOrderEntriesInEditors(true);
        }
      }
    }

    @Override
    public Object getDelegate() {
      return myDelegateModel;
    }
  }

  public interface ProxyDelegateAccessor {
    Object getDelegate();
  }

  private static Object[] unwrapParams(Object[] params) {
    if (params == null || params.length == 0) {
      return params;
    }
    final Object[] unwrappedParams = new Object[params.length];
    for (int idx = 0; idx < params.length; idx++) {
      Object param = params[idx];
      if (param != null && Proxy.isProxyClass(param.getClass())) {
        final InvocationHandler invocationHandler = Proxy.getInvocationHandler(param);
        if (invocationHandler instanceof ProxyDelegateAccessor) {
          param = ((ProxyDelegateAccessor)invocationHandler).getDelegate();
        }
      }
      unwrappedParams[idx] = param;
    }
    return unwrappedParams;
  }

  public @Nullable String getHelpTopic() {
    if (myEditors.isEmpty()) {
      return null;
    }
    final ModuleConfigurationEditor selectedEditor = getSelectedEditor();
    return selectedEditor != null ? selectedEditor.getHelpTopic() : null;
  }

  public void setModuleName(@NotNull String name) {
    myName = name;
    updateImportedModelWarning();
  }

  private class ModuleEditorPanel extends JPanel implements UiDataProvider {
    ModuleEditorPanel() {
      super(new BorderLayout());
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      sink.set(LangDataKeys.MODULE_CONTEXT, getModule());
    }
  }
}