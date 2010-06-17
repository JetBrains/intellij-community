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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.module.impl.ModuleConfigurationStateImpl;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 4, 2003
 *         Time: 6:29:56 PM
 */
@SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
public abstract class ModuleEditor implements Place.Navigator, Disposable {

  private final Project myProject;
  private JPanel myGenericSettingsPanel;
  private ModifiableRootModel myModifiableRootModel; // important: in order to correctly update OrderEntries UI use corresponding proxy for the model

  private static String ourSelectedTabName;

  private TabbedPaneWrapper myTabbedPane;
  private final ModulesProvider myModulesProvider;
  private String myName;
  private final Module myModule;

  private final List<ModuleConfigurationEditor> myEditors = new ArrayList<ModuleConfigurationEditor>();
  private ModifiableRootModel myModifiableRootModelProxy;

  private final EventDispatcher<ChangeListener> myEventDispatcher = EventDispatcher.create(ChangeListener.class);
  @NonNls private static final String METHOD_COMMIT = "commit";

  private History myHistory;

  public ModuleEditor(Project project, ModulesProvider modulesProvider,
                      @NotNull Module module) {
    myProject = project;
    myModulesProvider = modulesProvider;
    myModule = module;
    myName = module.getName();
  }

  public void init(final String selectedTab, History history) {
    myHistory = history;

    for (ModuleConfigurationEditor each : myEditors) {
      if (each instanceof ModuleElementsEditor) {
        ((ModuleElementsEditor)each).setHistory(myHistory);
      }
    }

    setSelectedTabName(selectedTab);
  }

  public abstract ProjectFacetsConfigurator getFacetsConfigurator();

  public interface ChangeListener extends EventListener {
    void moduleStateChanged(ModifiableRootModel moduleRootModel);
  }

  public void addChangeListener(ChangeListener listener) {
    myEventDispatcher.addListener(listener);
  }

  public void removeChangeListener(ChangeListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  @Nullable
  public Module getModule() {
    final Module[] all = myModulesProvider.getModules();
    for (Module each : all) {
      if (each == myModule) return myModule;
    }

    return myModulesProvider.getModule(myName);
  }

  public ModifiableRootModel getModifiableRootModel() {
    if (myModifiableRootModel == null) {
      final Module module = getModule();
      if (module != null) {
        myModifiableRootModel = ((ModuleRootManagerImpl)ModuleRootManager.getInstance(module)).getModifiableModel(new UIRootConfigurationAccessor(myProject));
      }
    }
    return myModifiableRootModel;
  }

  public OrderEntry[] getOrderEntries() {
    if (myModifiableRootModel == null) { // do not clone all model if not necessary
      return ModuleRootManager.getInstance(getModule()).getOrderEntries();
    }
    else {
      return myModifiableRootModel.getOrderEntries();
    }
  }

  public ModifiableRootModel getModifiableRootModelProxy() {
    if (myModifiableRootModelProxy == null) {
      final ModifiableRootModel rootModel = getModifiableRootModel();
      if (rootModel != null) {
        myModifiableRootModelProxy = (ModifiableRootModel)Proxy.newProxyInstance(
          getClass().getClassLoader(), new Class[]{ModifiableRootModel.class}, new ModifiableRootModelInvocationHandler(rootModel)
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
    for (ModuleConfigurationEditor moduleElementsEditor : myEditors) {
      if (moduleElementsEditor.isModified()) {
        return true;
      }
    }
    return false;
  }

  private void createEditors(Module module) {
    ModuleConfigurationEditorProvider[] providers = collectProviders(module);
    ModuleConfigurationState state = createModuleConfigurationState();
    List<ModuleLevelConfigurablesEditorProvider> moduleLevelProviders = new ArrayList<ModuleLevelConfigurablesEditorProvider>();
    for (ModuleConfigurationEditorProvider provider : providers) {
      if (provider instanceof ModuleLevelConfigurablesEditorProvider) {
        moduleLevelProviders.add((ModuleLevelConfigurablesEditorProvider)provider);
        continue;
      }
      processEditorsProvider(provider, state);
    }
    for (ModuleLevelConfigurablesEditorProvider provider : moduleLevelProviders) {
      processEditorsProvider(provider, state);
    }
  }

  private static ModuleConfigurationEditorProvider[] collectProviders(final Module module) {
    List<ModuleConfigurationEditorProvider> result = new ArrayList<ModuleConfigurationEditorProvider>();
    result.addAll(Arrays.asList(module.getComponents(ModuleConfigurationEditorProvider.class)));
    result.addAll(Arrays.asList(Extensions.getExtensions(ModuleConfigurationEditorProvider.EP_NAME, module)));
    return result.toArray(new ModuleConfigurationEditorProvider[result.size()]);
  }

  public ModuleConfigurationState createModuleConfigurationState() {
    return new ModuleConfigurationStateImpl(myProject, myModulesProvider) {
      @Override
      public ModifiableRootModel getRootModel() {
        return getModifiableRootModelProxy();
      }

      @Override
      public FacetsProvider getFacetsProvider() {
        return getFacetsConfigurator();
      }
    };
  }

  private void processEditorsProvider(final ModuleConfigurationEditorProvider provider, final ModuleConfigurationState state) {
    final ModuleConfigurationEditor[] editors = provider.createEditors(state);
    myEditors.addAll(Arrays.asList(editors));
  }

  private JPanel createPanel() {
    getModifiableRootModel(); //initialize model if needed
    getModifiableRootModelProxy();

    myGenericSettingsPanel = new ModuleEditorPanel();

    createEditors(getModule());

    JPanel northPanel = new JPanel(new GridBagLayout());

    myGenericSettingsPanel.add(northPanel, BorderLayout.NORTH);

    myTabbedPane = new TabbedPaneWrapper(this);

    for (ModuleConfigurationEditor editor : myEditors) {
      myTabbedPane.addTab(editor.getDisplayName(), editor.getIcon(), editor.createComponent(), null);
      editor.reset();
    }
    setSelectedTabName(ourSelectedTabName);

    myGenericSettingsPanel.add(myTabbedPane.getComponent(), BorderLayout.CENTER);
    myTabbedPane.addChangeListener(new javax.swing.event.ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        ourSelectedTabName = getSelectedTabName();
        if (myHistory != null) {
          myHistory.pushQueryPlace();
        }
      }
    });

    return myGenericSettingsPanel;
  }

  public ActionCallback navigateTo(@Nullable final Place place, final boolean requestFocus) {
    myTabbedPane.setSelectedTitle((String)place.getPath("moduleTab"));
    return new ActionCallback.Done();
  }

  public void queryPlace(@NotNull final Place place) {
    place.putPath("moduleTab", ourSelectedTabName);
  }

  public static String getSelectedTab(){
    return ourSelectedTabName;
  }

  private int getEditorTabIndex(final String editorName) {
    if (myTabbedPane != null && editorName != null) {
      final int tabCount = myTabbedPane.getTabCount();
      for (int idx = 0; idx < tabCount; idx++) {
        if (editorName.equals(myTabbedPane.getTitleAt(idx))) {
          return idx;
        }
      }
    }
    return -1;
  }

  public JPanel getPanel() {
    if (myGenericSettingsPanel == null) {
      myGenericSettingsPanel = createPanel();
    }

    return myGenericSettingsPanel;
  }

  public void moduleCountChanged() {
    updateOrderEntriesInEditors();
  }

  private void updateOrderEntriesInEditors() {
    if (getModule() != null) { //module with attached module libraries was deleted
      getPanel();  //init editor if needed
      for (final ModuleConfigurationEditor myEditor : myEditors) {
        myEditor.moduleStateChanged();
      }
      myEventDispatcher.getMulticaster().moduleStateChanged(getModifiableRootModelProxy());
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

  public void dispose() {
    try {
      for (final ModuleConfigurationEditor myEditor : myEditors) {
        myEditor.disposeUIResources();
      }

      myEditors.clear();

      if (myTabbedPane != null) {
        ourSelectedTabName = getSelectedTabName();
        myTabbedPane = null;
      }

      if (myModifiableRootModel != null) {
        myModifiableRootModel.dispose();
      }

      myGenericSettingsPanel = null;
    }
    finally {
      myModifiableRootModel = null;
      myModifiableRootModelProxy = null;
    }
  }

  public ModifiableRootModel apply() throws ConfigurationException {
    try {
      for (ModuleConfigurationEditor editor : myEditors) {
        editor.saveData();
        editor.apply();
      }

      return myModifiableRootModel;
    }
    finally {
      myModifiableRootModel = null;
      myModifiableRootModelProxy = null;
    }
  }

  public void canApply() throws ConfigurationException {
    for (ModuleConfigurationEditor editor : myEditors) {
      if (editor instanceof ModuleElementsEditor) {
        ((ModuleElementsEditor)editor).canApply();
      }
    }
  }

  public void canApply() throws ConfigurationException {
    for (ModuleConfigurationEditor editor : myEditors) {
      if (editor instanceof ModuleElementsEditor) {
        ((ModuleElementsEditor)editor).canApply();
      }
    }
  }

  public String getName() {
    return myName;
  }

  @Nullable
  public String getSelectedTabName() {
    return myTabbedPane == null || myTabbedPane.getSelectedIndex() == -1 ? null : myTabbedPane.getTitleAt(myTabbedPane.getSelectedIndex());
  }

  public void setSelectedTabName(@Nullable String name) {
    if (name != null) {
      getPanel();
      final int editorTabIndex = getEditorTabIndex(name);
      if (editorTabIndex >= 0 && editorTabIndex < myTabbedPane.getTabCount()) {
        myTabbedPane.setSelectedIndex(editorTabIndex);
        ourSelectedTabName = name;
      }
    }
  }

  @Nullable
  public ModuleConfigurationEditor getEditor(@NotNull String tabName) {
    int index = getEditorTabIndex(tabName);
    if (0 <= index && index < myEditors.size()) {
      return myEditors.get(index);
    }
    return null;
  }

  private class ModifiableRootModelInvocationHandler implements InvocationHandler {
    private final ModifiableRootModel myDelegateModel;
    @NonNls private final Set<String> myCheckedNames = new HashSet<String>(
      Arrays.asList("addOrderEntry", "addLibraryEntry", "addInvalidLibrary", "addModuleOrderEntry", "addInvalidModuleEntry",
                    "removeOrderEntry", "setSdk", "inheritSdk", "inheritCompilerOutputPath", "setExcludeOutput", "replaceEntryOfType"));

    ModifiableRootModelInvocationHandler(ModifiableRootModel model) {
      myDelegateModel = model;
    }

    public Object invoke(Object object, Method method, Object[] params) throws Throwable {
      final boolean needUpdate = myCheckedNames.contains(method.getName());
      try {
        final Object result = method.invoke(myDelegateModel, unwrapParams(params));
        if (result instanceof LibraryTable) {
          return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{LibraryTable.class},
                                        new LibraryTableInvocationHandler((LibraryTable)result));
        }
        return result;
      }
      catch (InvocationTargetException e) {
        throw e.getCause();
      }
      finally {
        if (needUpdate) {
          updateOrderEntriesInEditors();
        }
      }
    }
  }

  private class LibraryTableInvocationHandler implements InvocationHandler {
    private final LibraryTable myDelegateTable;
    @NonNls private final Set<String> myCheckedNames = new HashSet<String>(Arrays.asList("removeLibrary" /*,"createLibrary"*/));

    LibraryTableInvocationHandler(LibraryTable table) {
      myDelegateTable = table;
    }

    public Object invoke(Object object, Method method, Object[] params) throws Throwable {
      final boolean needUpdate = myCheckedNames.contains(method.getName());
      try {
        final Object result = method.invoke(myDelegateTable, unwrapParams(params));
        if (result instanceof Library) {
          return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Library.class},
                                        new LibraryInvocationHandler((Library)result));
        }
        else if (result instanceof LibraryTable.ModifiableModel) {
          return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{LibraryTable.ModifiableModel.class},
                                        new LibraryTableModelInvocationHandler((LibraryTable.ModifiableModel)result));
        }
        if (result instanceof Library[]) {
          Library[] libraries = (Library[])result;
          for (int idx = 0; idx < libraries.length; idx++) {
            Library library = libraries[idx];
            libraries[idx] =
            (Library)Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Library.class},
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
          updateOrderEntriesInEditors();
        }
      }
    }

  }

  private class LibraryInvocationHandler implements InvocationHandler, ProxyDelegateAccessor {
    private final Library myDelegateLibrary;

    LibraryInvocationHandler(Library delegateLibrary) {
      myDelegateLibrary = delegateLibrary;
    }

    public Object invoke(Object object, Method method, Object[] params) throws Throwable {
      try {
        final Object result = method.invoke(myDelegateLibrary, unwrapParams(params));
        if (result instanceof Library.ModifiableModel) {
          return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Library.ModifiableModel.class},
                                        new LibraryModifiableModelInvocationHandler((Library.ModifiableModel)result));
        }
        return result;
      }
      catch (InvocationTargetException e) {
        throw e.getCause();
      }
    }

    public Object getDelegate() {
      return myDelegateLibrary;
    }
  }

  private class LibraryModifiableModelInvocationHandler implements InvocationHandler {
    private final Library.ModifiableModel myDelegateModel;

    LibraryModifiableModelInvocationHandler(Library.ModifiableModel delegateModel) {
      myDelegateModel = delegateModel;
    }

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
          updateOrderEntriesInEditors();
        }
      }
    }
  }

  private class LibraryTableModelInvocationHandler implements InvocationHandler {
    private final LibraryTable.ModifiableModel myDelegateModel;

    LibraryTableModelInvocationHandler(LibraryTable.ModifiableModel delegateModel) {
      myDelegateModel = delegateModel;
    }

    public Object invoke(Object object, Method method, Object[] params) throws Throwable {
      final boolean needUpdate = METHOD_COMMIT.equals(method.getName());
      try {
        Object result = method.invoke(myDelegateModel, unwrapParams(params));
        if (result instanceof Library[]) {
          Library[] libraries = (Library[])result;
          for (int idx = 0; idx < libraries.length; idx++) {
            Library library = libraries[idx];
            libraries[idx] =
            (Library)Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Library.class},
                                            new LibraryInvocationHandler(library));
          }
        }
        if (result instanceof Library) {
          result =
          Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Library.class},
                                 new LibraryInvocationHandler((Library)result));
        }
        return result;
      }
      catch (InvocationTargetException e) {
        throw e.getCause();
      }
      finally {
        if (needUpdate) {
          updateOrderEntriesInEditors();
        }
      }
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

  @Nullable
  public String getHelpTopic() {
    if (myTabbedPane == null || myEditors.isEmpty()) {
      return null;
    }
    final int selectedIdx = myTabbedPane.getSelectedIndex();
    if (selectedIdx == -1) {
      return null;
    }
    final ModuleConfigurationEditor moduleElementsEditor = myEditors.get(selectedIdx);
    return moduleElementsEditor.getHelpTopic();
  }

  public void setModuleName(final String name) {
    myName = name;
  }

  private class ModuleEditorPanel extends JPanel implements DataProvider{
    public ModuleEditorPanel() {
      super(new BorderLayout());
    }

    public Object getData(String dataId) {
      if (LangDataKeys.MODULE_CONTEXT.is(dataId)) {
        return getModule();
      }
      return null;
    }

  }

  public void setHistory(final History history) {
  }
}
