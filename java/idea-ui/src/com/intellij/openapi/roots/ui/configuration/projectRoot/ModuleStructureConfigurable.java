/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.CommonBundle;
import com.intellij.facet.Facet;
import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.facet.impl.ui.actions.AddFacetToModuleAction;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.impl.FlattenModulesToggleAction;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.ModuleGroupingImplementation;
import com.intellij.ide.projectView.impl.ModuleGroupingTreeHelper;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.NamePathComponent;
import com.intellij.ide.util.projectWizard.ProjectWizardUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.*;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ClonableOrderEntry;
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl;
import com.intellij.openapi.roots.impl.RootModelImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.LibraryProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ModuleProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureDaemonAnalyzer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.ui.navigation.Place;
import com.intellij.util.PathUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class ModuleStructureConfigurable extends BaseStructureConfigurable implements Place.Navigator {
  private static final Comparator<MyNode> NODE_COMPARATOR = (o1, o2) -> {
    final NamedConfigurable configurable1 = o1.getConfigurable();
    final NamedConfigurable configurable2 = o2.getConfigurable();
    if (configurable1.getClass() == configurable2.getClass()) {
      return StringUtil.naturalCompare(o1.getDisplayName(), o2.getDisplayName());
    }
    final Object editableObject1 = configurable1.getEditableObject();
    final Object editableObject2 = configurable2.getEditableObject();

    if (editableObject2 instanceof Module && editableObject1 instanceof ModuleGroup) return -1;
    if (editableObject1 instanceof Module && editableObject2 instanceof ModuleGroup) return 1;

    if (editableObject2 instanceof Module && editableObject1 instanceof String) return 1;
    if (editableObject1 instanceof Module && editableObject2 instanceof String) return -1;

    if (editableObject2 instanceof Module && editableObject1 instanceof Facet) return 1;
    if (editableObject1 instanceof Module && editableObject2 instanceof Facet) return -1;

    if (editableObject2 instanceof ModuleGroup && editableObject1 instanceof String) return 1;
    if (editableObject1 instanceof ModuleGroup && editableObject2 instanceof String) return -1;

    return 0;
  };

  private boolean myHideModuleGroups;
  private boolean myFlattenModules;

  private final ModuleManager myModuleManager;

  private final FacetEditorFacadeImpl myFacetEditorFacade = new FacetEditorFacadeImpl(this, TREE_UPDATER);

  private final List<RemoveConfigurableHandler<?>> myRemoveHandlers;

  public ModuleStructureConfigurable(Project project, ModuleManager manager) {
    super(project);
    myModuleManager = manager;
    myRemoveHandlers = new ArrayList<>();
    myRemoveHandlers.add(new ModuleRemoveHandler());
    myRemoveHandlers.add(new FacetInModuleRemoveHandler());
    for (ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
      myRemoveHandlers.addAll(extension.getRemoveHandlers());
    }
  }

  @Override
  protected String getComponentStateKey() {
    return "ModuleStructureConfigurable.UI";
  }

  @Override
  protected void initTree() {
    super.initTree();
    myTree.setRootVisible(false);
  }

  @Override
  protected ArrayList<AnAction> getAdditionalActions() {
    final ArrayList<AnAction> result = new ArrayList<>();
    result.add(ActionManager.getInstance().getAction(IdeActions.GROUP_MOVE_MODULE_TO_GROUP));
    return result;
  }

  @Override
  public void addNode(MyNode nodeToAdd, MyNode parent) {
    super.addNode(nodeToAdd, parent);
  }

  @Override
  @NotNull
  protected ArrayList<AnAction> createActions(final boolean fromPopup) {
    final ArrayList<AnAction> result = super.createActions(fromPopup);
    if (fromPopup) {
      result.add(Separator.getInstance());
      result.add(new FlattenModulesToggleAction(myProject, () -> true, () -> myFlattenModules, (value) -> {
        myFlattenModules = value;
        regroupModules();
      }));
      result.add(new HideGroupsAction());
      addCollapseExpandActions(result);
    }
    return result;
  }

  @Override
  @NotNull
  protected List<? extends AnAction> createCopyActions(boolean fromPopup) {
    return Collections.singletonList(new MyCopyAction());
  }

  @Override
  protected void loadTree() {
    createProjectNodes();

    getTreeModel().reload();

    myUiDisposed = false;
  }

  @NotNull
  @Override
  protected Collection<? extends ProjectStructureElement> getProjectStructureElements() {
    final List<ProjectStructureElement> result = new ArrayList<>();
    for (Module module : myModuleManager.getModules()) {
      result.add(new ModuleProjectStructureElement(myContext, module));
    }
    return result;
  }

  @Override
  protected void updateSelection(@Nullable NamedConfigurable configurable) {
    FacetStructureConfigurable.getInstance(myProject).disposeMultipleSettingsEditor();
    ApplicationManager.getApplication().assertIsDispatchThread();
    super.updateSelection(configurable);
    if (configurable != null) {
      updateModuleEditorSelection(configurable);
    }
  }


  @Override
  protected boolean isAutoScrollEnabled() {
    return myAutoScrollEnabled;
  }

  @Override
  protected boolean updateMultiSelection(final List<NamedConfigurable> selectedConfigurables) {
    return FacetStructureConfigurable.getInstance(myProject).updateMultiSelection(selectedConfigurables, getDetailsComponent());
  }

  private void updateModuleEditorSelection(final NamedConfigurable configurable) {
    if (configurable instanceof ModuleConfigurable) {
      final ModuleConfigurable moduleConfigurable = (ModuleConfigurable)configurable;
      final ModuleEditor editor = moduleConfigurable.getModuleEditor();
      if (editor != null) { //already deleted
        editor.init(myHistory);
      }
    }
    if (configurable instanceof FacetConfigurable) {
      ((FacetConfigurable)configurable).getEditor().onFacetSelected();
    }
  }


  private void createProjectNodes() {
    ModuleGrouper moduleGrouper = getModuleGrouper();
    ModuleGroupingTreeHelper<Module, MyNode> helper = ModuleGroupingTreeHelper.forEmptyTree(!myHideModuleGroups && !myFlattenModules,
                                                                                    ModuleGroupingTreeHelper.createDefaultGrouping(moduleGrouper),
                                                                                    ModuleStructureConfigurable::createModuleGroupNode,
                                                                                    m -> createModuleNode(m, moduleGrouper), getNodeComparator());
    helper.createModuleNodes(Arrays.asList(myModuleManager.getModules()), myRoot, getTreeModel());
    if (containsSecondLevelNodes(myRoot)) {
      myTree.setShowsRootHandles(true);
    }
    sortDescendants(myRoot);
    if (myProject.isDefault()) {  //do not add modules node in case of template project
      myRoot.removeAllChildren();
    }

    addRootNodesFromExtensions(myRoot, myProject);
    //final LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
    //final LibrariesModifiableModel projectLibrariesProvider = new LibrariesModifiableModel(table);
    //myLevel2Providers.put(LibraryTablesRegistrar.PROJECT_LEVEL, projectLibrariesProvider);
    //
    //myProjectNode.add(myLevel2Nodes.get(LibraryTablesRegistrar.PROJECT_LEVEL));
  }

  private static boolean containsSecondLevelNodes(TreeNode rootNode) {
    int count = rootNode.getChildCount();
    for (int i = 0; i < count; i++) {
      TreeNode child = rootNode.getChildAt(i);
      if (child.getChildCount() > 0) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private ModuleNode createModuleNode(Module module, ModuleGrouper moduleGrouper) {
    ModuleConfigurable configurable = new ModuleConfigurable(myContext.myModulesConfigurator, module, TREE_UPDATER, moduleGrouper);
    List<String> groupPath = moduleGrouper.getModuleAsGroupPath(module);
    ModuleNode node = new ModuleNode(configurable, groupPath != null ? new ModuleGroup(groupPath) : null);
    myFacetEditorFacade.addFacetsNodes(module, node);
    addNodesFromExtensions(module, node);
    return node;
  }

  @NotNull
  private static MyNode createModuleGroupNode(ModuleGroup moduleGroup) {
    final NamedConfigurable moduleGroupConfigurable = new TextConfigurable<>(moduleGroup, moduleGroup.toString(),
                                                                             ProjectBundle.message("module.group.banner.text", moduleGroup.toString()),
                                                                             ProjectBundle.message("project.roots.module.groups.text"),
                                                                             PlatformIcons.CLOSED_MODULE_GROUP_ICON);
    return new ModuleGroupNodeImpl(moduleGroupConfigurable, moduleGroup);
  }

  private void addRootNodesFromExtensions(final MyNode root, final Project project) {
    for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
      extension.addRootNodes(root, project, TREE_UPDATER);
    }
  }

  private void addNodesFromExtensions(final Module module, final MyNode moduleNode) {
    for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
      extension.addModuleNodeChildren(module, moduleNode, TREE_UPDATER);
    }
  }

  public boolean updateProjectTree(final Module[] modules) {
    if (myRoot.getChildCount() == 0) return false; //isn't visible
    List<Pair<MyNode, Module>> nodes = new ArrayList<>(modules.length);
    for (Module module : modules) {
      MyNode node = findModuleNode(module);
      LOG.assertTrue(node != null, "Module " + module.getName() + " is not in project.");
      nodes.add(Pair.create(node, module));
    }
    ModuleGroupingTreeHelper<Module, MyNode> helper = createGroupingHelper();
    helper.moveModuleNodesToProperGroup(nodes, myRoot, getTreeModel(), myTree);
    return true;
  }

  private DefaultTreeModel getTreeModel() {
    return (DefaultTreeModel)myTree.getModel();
  }

  @NotNull
  private ModuleGroupingTreeHelper<Module, MyNode> createGroupingHelper() {
    ModuleGrouper grouper = getModuleGrouper();
    ModuleGroupingImplementation<Module> grouping = ModuleGroupingTreeHelper.createDefaultGrouping(grouper);
    return ModuleGroupingTreeHelper.forTree(myRoot, (node) -> node instanceof ModuleGroupNode ? ((ModuleGroupNode)node).getModuleGroup() : null,
                                            (node) -> node instanceof ModuleNode ? ((ModuleNode)node).getModule() : null,
                                            !myHideModuleGroups && !myFlattenModules,
                                            grouping, ModuleStructureConfigurable::createModuleGroupNode,
                                            module -> createModuleNode(module, grouper), getNodeComparator());
  }

  @Override
  protected Comparator<MyNode> getNodeComparator() {
    List<Comparator<MyNode>> comparators = ContainerUtil
      .mapNotNull(ModuleStructureExtension.EP_NAME.getExtensions(), moduleStructureExtension -> moduleStructureExtension.getNodeComparator());
    return new MergingComparator<>(ContainerUtil.concat(comparators, Collections.singletonList(NODE_COMPARATOR)));
  }

  @Override
  public void init(final StructureConfigurableContext context) {
    super.init(context);

    addItemsChangeListener(new ItemsChangeListener() {
      @Override
      public void itemChanged(@Nullable Object deletedItem) {
        if (deletedItem instanceof Library) {
          final Library library = (Library)deletedItem;
          final MyNode node = findNodeByObject(myRoot, library);
          if (node != null) {
            final TreeNode parent = node.getParent();
            node.removeFromParent();
            getTreeModel().reload(parent);
          }
          myContext.getDaemonAnalyzer().removeElement(new LibraryProjectStructureElement(myContext, library));
        }
      }

      @Override
      public void itemsExternallyChanged() {
        //do nothing
      }
    });
  }

  @Override
  public void reset() {
    super.reset();
    for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
      extension.reset(myProject);
    }
  }

  @Override
  public void apply() throws ConfigurationException {
    checkForEmptyAndDuplicatedNames(ProjectBundle.message("rename.message.prefix.module"),
                                    ProjectBundle.message("rename.module.title"), ModuleConfigurable.class);

    // let's apply extensions first, since they can write to/commit modifiable models
    for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
      if (extension.isModified()) {
        extension.apply();
      }
    }

    if (myContext.myModulesConfigurator.isModified()) {
      myContext.myModulesConfigurator.apply();
    }

    for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
      extension.afterModelCommit();
    }
  }

  @Override
  public boolean isModified() {
    if (myContext.myModulesConfigurator.isModified()) {
      return true;
    }

    for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
      if (extension.isModified()) {
        return true;
      }
    }

    return false;
  }

  @Override
  public void disposeUIResources() {
    super.disposeUIResources();
    myFacetEditorFacade.clearMaps(true);
    myContext.myModulesConfigurator.disposeUIResources();
    super.disposeUIResources();

    for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
      extension.disposeUIResources();
    }
  }

  @Override
  public void dispose() {}

  @NotNull
  @Override
  public JComponent createComponent() {
    return new MyDataProviderWrapper(super.createComponent());
  }

  @Override
  protected void processRemovedItems() {
    // do nothing
  }

  @Override
  protected boolean wasObjectStored(Object editableObject) {
    return false;
  }

  @Override
  public String getDisplayName() {
    return ProjectBundle.message("project.roots.display.name");
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpTopic() {
    final String topic = super.getHelpTopic();
    if (topic != null) {
      return topic;
    }
    return "reference.settingsdialog.project.structure.module";
  }

  public ActionCallback selectOrderEntry(@NotNull Module module, @Nullable OrderEntry orderEntry) {
    for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
      final ActionCallback callback = extension.selectOrderEntry(module, orderEntry);
      if (callback != null) {
        return callback;
      }
    }

    Place p = new Place();
    p.putPath(ProjectStructureConfigurable.CATEGORY, this);
    Runnable r = null;

    final MasterDetailsComponent.MyNode node = findModuleNode(module);
    if (node != null) {
      p.putPath(TREE_OBJECT, module);
      p.putPath(ModuleEditor.SELECTED_EDITOR_NAME, ClasspathEditor.NAME);
      r = () -> {
        if (orderEntry != null) {
          ModuleEditor moduleEditor = ((ModuleConfigurable)node.getConfigurable()).getModuleEditor();
          ModuleConfigurationEditor editor = moduleEditor.getEditor(ClasspathEditor.NAME);
          if (editor instanceof ClasspathEditor) {
            ((ClasspathEditor)editor).selectOrderEntry(orderEntry);
          }
        }
      };
    }
    final ActionCallback result = ProjectStructureConfigurable.getInstance(myProject).navigateTo(p, true);
    return r != null ? result.doWhenDone(r) : result;
  }

  private ModuleGrouper getModuleGrouper() {
    return ModuleGrouper.instanceFor(myProject, myContext.myModulesConfigurator.getModuleModel());
  }


  public static ModuleStructureConfigurable getInstance(final Project project) {
    return ServiceManager.getService(project, ModuleStructureConfigurable.class);
  }

  public Project getProject() {
    return myProject;
  }

  public Module[] getModules() {
    if (myContext.myModulesConfigurator != null) {
      final ModifiableModuleModel model = myContext.myModulesConfigurator.getModuleModel();
      return model.getModules();
    }
    else {
      return myModuleManager.getModules();
    }
  }

  public void removeLibraryOrderEntry(final Module module, final Library library) {
    final ModuleEditor moduleEditor = myContext.myModulesConfigurator.getModuleEditor(module);
    LOG.assertTrue(moduleEditor != null, "Current module editor was not initialized");
    final ModifiableRootModel modelProxy = moduleEditor.getModifiableRootModelProxy();
    final OrderEntry[] entries = modelProxy.getOrderEntries();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry && Comparing.strEqual(entry.getPresentableName(), library.getName())) {
        modelProxy.removeOrderEntry(entry);
        break;
      }
    }

    myContext.getDaemonAnalyzer().queueUpdate(new ModuleProjectStructureElement(myContext, module));
    myTree.repaint();
  }

  public void addLibraryOrderEntry(final Module module, final Library library) {
    final ModuleEditor moduleEditor = myContext.myModulesConfigurator.getModuleEditor(module);
    LOG.assertTrue(moduleEditor != null, "Current module editor was not initialized");
    final ModifiableRootModel modelProxy = moduleEditor.getModifiableRootModelProxy();
    final OrderEntry[] entries = modelProxy.getOrderEntries();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry && Comparing.strEqual(entry.getPresentableName(), library.getName())) {
        if (Messages.showYesNoDialog(module.getProject(),
                                     ProjectBundle.message("project.roots.replace.library.entry.message", entry.getPresentableName()),
                                     ProjectBundle.message("project.roots.replace.library.entry.title"),
                                     Messages.getInformationIcon()) == Messages.YES) {
          modelProxy.removeOrderEntry(entry);
          break;
        }
      }
    }
    modelProxy.addLibraryEntry(library);
    myContext.getDaemonAnalyzer().queueUpdate(new ModuleProjectStructureElement(myContext, module));
    myTree.repaint();
  }

  @Nullable
  public MyNode findModuleNode(final Module module) {
    return findNodeByObject(myRoot, module);
  }

  public FacetEditorFacadeImpl getFacetEditorFacade() {
    return myFacetEditorFacade;
  }

  public ProjectFacetsConfigurator getFacetConfigurator() {
    return myContext.myModulesConfigurator.getFacetsConfigurator();
  }

  private void addModule(boolean anImport) {
    final List<Module> modules = myContext.myModulesConfigurator.addModule(myTree, anImport);
    if (modules != null) {
      for (Module module : modules) {
        addModuleNode(module);
      }
    }
  }

  private void addModuleNode(final Module module) {
    final TreePath selectionPath = myTree.getSelectionPath();
    MyNode parent = null;
    if (selectionPath != null) {
      MyNode selected = (MyNode)selectionPath.getLastPathComponent();
      final Object o = selected.getConfigurable().getEditableObject();
      if (o instanceof ModuleGroup) {
        myContext.myModulesConfigurator.getModuleModel().setModuleGroupPath(module, ((ModuleGroup)o).getGroupPath());
        parent = selected;
      }
      else if (o instanceof Module) { //create near selected
        final ModifiableModuleModel modifiableModuleModel = myContext.myModulesConfigurator.getModuleModel();
        final String[] groupPath = modifiableModuleModel.getModuleGroupPath((Module)o);
        if (groupPath != null) {
          modifiableModuleModel.setModuleGroupPath(module, groupPath);
          parent = findNodeByObject(myRoot, new ModuleGroup(Arrays.asList(groupPath)));
        }
      }
    }
    if (parent == null) parent = myRoot;
    MyNode node = createModuleNode(module, getModuleGrouper());
    TreeUtil.insertNode(node, parent, getTreeModel(), false, getNodeComparator());
    selectNodeInTree(node);
    final ProjectStructureDaemonAnalyzer daemonAnalyzer = myContext.getDaemonAnalyzer();
    daemonAnalyzer.queueUpdate(new ModuleProjectStructureElement(myContext, module));
    daemonAnalyzer.queueUpdateForAllElementsWithErrors(); //missing modules added
  }

  @Nullable
  public Module getSelectedModule() {
    final Object selectedObject = getSelectedObject();
    if (selectedObject instanceof Module) {
      return (Module)selectedObject;
    }
    if (selectedObject instanceof Library) {
      if (((Library)selectedObject).getTable() == null) {
        final MyNode node = (MyNode)myTree.getSelectionPath().getLastPathComponent();
        return (Module)((MyNode)node.getParent()).getConfigurable().getEditableObject();
      }
    }
    return null;
  }

  @Override
  @NotNull
  @NonNls
  public String getId() {
    return "project.structure";
  }

  @Nullable
  public Module getModule(final String moduleName) {
    if (moduleName == null) return null;
    return (myContext != null && myContext.myModulesConfigurator != null)
           ? myContext.myModulesConfigurator.getModule(moduleName)
           : myModuleManager.findModuleByName(moduleName);
  }

  public StructureConfigurableContext getContext() {
    return myContext;
  }

  private static boolean canBeCopiedByExtension(final NamedConfigurable configurable) {
    for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
      if (extension.canBeCopied(configurable)) {
        return true;
      }
    }
    return false;
  }

  private void copyByExtension(final NamedConfigurable configurable) {
    for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
      extension.copy(configurable, TREE_UPDATER);
    }
  }

  private class ModuleNode extends MyNode implements ModuleGroupNode {
    private final ModuleGroup myModuleAsGroup;

    public ModuleNode(@NotNull ModuleConfigurable configurable, @Nullable ModuleGroup moduleAsGroup) {
      super(configurable);
      myModuleAsGroup = moduleAsGroup;
    }

    public ModuleGroup getModuleGroup() {
      return myModuleAsGroup;
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return myFlattenModules ? getModule().getName() : getModuleGrouper().getShortenedName(getModule());
    }

    private ModuleGrouper getModuleGrouper() {
      return getConfigurable().getModuleGrouper();
    }

    @Override
    public ModuleConfigurable getConfigurable() {
      return (ModuleConfigurable)super.getConfigurable();
    }

    private Module getModule() {
      return getConfigurable().getModule();
    }

    @Override
    protected void reloadNode(DefaultTreeModel treeModel) {
      boolean autoScrollWasEnabled = myAutoScrollEnabled;
      try {
        myAutoScrollEnabled = false;
        ModuleGroupingTreeHelper<Module, MyNode> helper = createGroupingHelper();
        MyNode newNode = helper.moveModuleNodeToProperGroup(this, getModule(), myRoot, treeModel, myTree);
        treeModel.reload(newNode);
      }
      finally {
        myAutoScrollEnabled = autoScrollWasEnabled;
      }
    }
  }

  private interface ModuleGroupNode extends MutableTreeNode {
    @Nullable
    ModuleGroup getModuleGroup();
  }

  private static class ModuleGroupNodeImpl extends MyNode implements ModuleGroupNode {
    private final ModuleGroup myModuleGroup;

    public ModuleGroupNodeImpl(@NotNull NamedConfigurable configurable, @NotNull ModuleGroup moduleGroup) {
      super(configurable, true);
      myModuleGroup = moduleGroup;
    }

    @Override
    public ModuleGroup getModuleGroup() {
      return myModuleGroup;
    }
  }

  private class FacetInModuleRemoveHandler extends RemoveConfigurableHandler<Facet> {
    public FacetInModuleRemoveHandler() {
      super(FacetConfigurable.class);
    }

    @Override
    public boolean remove(@NotNull Collection<Facet> facets) {
      for (Facet facet : facets) {
        List<Facet> removed = myContext.myModulesConfigurator.getFacetsConfigurator().removeFacet(facet);
        FacetStructureConfigurable.getInstance(myProject).removeFacetNodes(removed);
      }
      return true;
    }
  }

  private class ModuleRemoveHandler extends RemoveConfigurableHandler<Module> {
    public ModuleRemoveHandler() {
      super(ModuleConfigurable.class);
    }

    @Override
    public boolean remove(@NotNull Collection<Module> modules) {
      ModulesConfigurator modulesConfigurator = myContext.myModulesConfigurator;
      List<Module> deleted = modulesConfigurator.deleteModules(modules);
      if (deleted.isEmpty()) {
        return false;
      }
      for (Module module : deleted) {
        List<Facet> removed = modulesConfigurator.getFacetsConfigurator().removeAllFacets(module);
        FacetStructureConfigurable.getInstance(myProject).removeFacetNodes(removed);
        myContext.getDaemonAnalyzer().removeElement(new ModuleProjectStructureElement(myContext, module));

        for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
          extension.moduleRemoved(module);
        }
      }
      return true;
    }
  }

  private class MyDataProviderWrapper extends JPanel implements DataProvider {
    public MyDataProviderWrapper(final JComponent component) {
      super(new BorderLayout());
      add(component, BorderLayout.CENTER);
    }

    @Override
    @Nullable
    public Object getData(@NonNls String dataId) {
      if (LangDataKeys.MODULE_CONTEXT_ARRAY.is(dataId)) {
        final TreePath[] paths = myTree.getSelectionPaths();
        if (paths != null) {
          ArrayList<Module> modules = new ArrayList<>();
          for (TreePath path : paths) {
            MyNode node = (MyNode)path.getLastPathComponent();
            final NamedConfigurable configurable = node.getConfigurable();
            LOG.assertTrue(configurable != null, "already disposed");
            final Object o = configurable.getEditableObject();
            if (o instanceof Module) {
              modules.add((Module)o);
            }
          }
          return !modules.isEmpty() ? modules.toArray(new Module[modules.size()]) : null;
        }
      }
      if (LangDataKeys.MODULE_CONTEXT.is(dataId)) {
        return getSelectedModule();
      }
      if (LangDataKeys.MODIFIABLE_MODULE_MODEL.is(dataId)) {
        return myContext.myModulesConfigurator.getModuleModel();
      }

      return null;
    }
  }

  private class HideGroupsAction extends ToggleAction implements DumbAware {

    public HideGroupsAction() {
      super("", "", AllIcons.ObjectBrowser.CompactEmptyPackages);
    }

    @Override
    public void update(@NotNull
                       final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      String text;
      text = ProjectBundle
        .message(myHideModuleGroups ? "project.roots.plain.mode.action.text.enabled" : "project.roots.plain.mode.action.text.disabled");
      presentation.setText(text);
      presentation.setDescription(text);

      if (myContext.myModulesConfigurator != null) {
        presentation.setVisible(myContext.myModulesConfigurator.getModuleModel().hasModuleGroups());
      }
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myHideModuleGroups;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myHideModuleGroups = state;
      regroupModules();
    }
  }

  private void regroupModules() {
    DefaultMutableTreeNode selection = null;
    final TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath != null) {
      selection = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
    }
    createGroupingHelper().moveAllModuleNodesToProperGroups(myRoot, getTreeModel());
    if (selection != null) {
      TreeUtil.selectInTree(selection, true, myTree);
    }
  }

  @Override
  protected AbstractAddGroup createAddAction() {
    return new AbstractAddGroup(ProjectBundle.message("add.new.header.text")) {
      @Override
      @NotNull
      public AnAction[] getChildren(@Nullable
                                    final AnActionEvent e) {

        ArrayList<AnAction> result = new ArrayList<>();

        AnAction addModuleAction = new AddModuleAction(false);
        addModuleAction.getTemplatePresentation().setText("New Module");
        result.add(addModuleAction);

        AnAction importModuleAction = new AddModuleAction(true);
        importModuleAction.getTemplatePresentation().setText("Import Module");
        importModuleAction.getTemplatePresentation().setIcon(AllIcons.ToolbarDecorator.Import);
        result.add(importModuleAction);

        final Collection<AnAction> actions = AddFacetToModuleAction.createAddFrameworkActions(myFacetEditorFacade, myProject);
        if (!actions.isEmpty()) {
          result.add(new Separator(ProjectBundle.message("add.group.framework.separator")));
          result.addAll(actions);
        }

        final NullableComputable<MyNode> selectedNodeRetriever = () -> {
          final TreePath selectionPath = myTree.getSelectionPath();
          final Object lastPathComponent = selectionPath == null ? null : selectionPath.getLastPathComponent();
          if (lastPathComponent instanceof MyNode) {
            return (MyNode)lastPathComponent;
          }
          return null;
        };

        Collection<AnAction> actionsFromExtensions = new ArrayList<>();
        for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
          actionsFromExtensions.addAll(extension.createAddActions(selectedNodeRetriever, TREE_UPDATER, myProject, myRoot));
        }

        if (!actionsFromExtensions.isEmpty() && !result.isEmpty()) {
          result.add(new Separator());
        }
        result.addAll(actionsFromExtensions);
        return result.toArray(new AnAction[result.size()]);
      }
    };
  }

  @Override
  protected List<RemoveConfigurableHandler<?>> getRemoveHandlers() {
    return myRemoveHandlers;
  }

  @Override
  @Nullable
  protected String getEmptySelectionString() {
    return ProjectBundle.message("empty.module.selection.string");
  }

  private class MyCopyAction extends AnAction implements DumbAware {
    private MyCopyAction() {
      super(CommonBundle.message("button.copy"), CommonBundle.message("button.copy"), COPY_ICON);
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      final NamedConfigurable namedConfigurable = getSelectedConfigurable();
      if (namedConfigurable instanceof ModuleConfigurable) {
        try {
          final ModuleEditor moduleEditor = ((ModuleConfigurable)namedConfigurable).getModuleEditor();
          final String modulePresentation = IdeBundle.message("project.new.wizard.module.identification");
          final NamePathComponent component = new NamePathComponent(IdeBundle.message("label.module.name"), IdeBundle
            .message("label.component.file.location", StringUtil.capitalize(modulePresentation)), IdeBundle
                                                                      .message("title.select.project.file.directory", modulePresentation),
                                                                    IdeBundle.message("description.select.project.file.directory",
                                                                                      StringUtil.capitalize(modulePresentation)), true,
                                                                    false);
          final Module originalModule = moduleEditor.getModule();
          if (originalModule != null) {
            component.setPath(FileUtil.toSystemDependentName(PathUtil.getParentPath(originalModule.getModuleFilePath())));
          }

          final DialogBuilder dialogBuilder = new DialogBuilder(myTree);
          dialogBuilder.setTitle(ProjectBundle.message("copy.module.dialog.title"));
          dialogBuilder.setCenterPanel(component);
          dialogBuilder.setPreferredFocusComponent(component.getNameComponent());
          dialogBuilder.setOkOperation(() -> {
            final String name = component.getNameValue();
            if (name.length() == 0) {
              Messages.showErrorDialog(ProjectBundle.message("enter.module.copy.name.error.message"), CommonBundle.message("title.error"));
              return;
            }
            if (getModule(name) != null) {
              Messages
                .showErrorDialog(ProjectBundle.message("module.0.already.exists.error.message", name), CommonBundle.message("title.error"));
              return;
            }

            if (component.getPath().length() == 0) {
              Messages.showErrorDialog(IdeBundle.message("prompt.enter.project.file.location", modulePresentation),
                                       CommonBundle.message("title.error"));
              return;
            }
            if (!ProjectWizardUtil
              .createDirectoryIfNotExists(IdeBundle.message("directory.project.file.directory", modulePresentation), component.getPath(),
                                          true)) {
              Messages.showErrorDialog(ProjectBundle.message("path.0.is.invalid.error.message", component.getPath()),
                                       CommonBundle.message("title.error"));
              return;
            }
            dialogBuilder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
          });
          if (dialogBuilder.show() != DialogWrapper.OK_EXIT_CODE) return;

          final ModifiableRootModel rootModel = moduleEditor.getModifiableRootModel();
          final String path = component.getPath();
          final ModuleBuilder builder = new ModuleBuilder() {
            @Override
            public void setupRootModel(final ModifiableRootModel modifiableRootModel) throws ConfigurationException {
              if (rootModel.isSdkInherited()) {
                modifiableRootModel.inheritSdk();
              }
              else {
                modifiableRootModel.setSdk(rootModel.getSdk());
              }

              modifiableRootModel.getModuleExtension(CompilerModuleExtension.class).inheritCompilerOutputPath(true);

              modifiableRootModel.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(
                LanguageLevelModuleExtensionImpl.getInstance(rootModel.getModule()).getLanguageLevel());

              for (OrderEntry entry : rootModel.getOrderEntries()) {
                if (entry instanceof JdkOrderEntry) continue;
                if (entry instanceof ModuleSourceOrderEntry) continue;
                if (entry instanceof ClonableOrderEntry) {
                  modifiableRootModel.addOrderEntry(((ClonableOrderEntry)entry).cloneEntry((RootModelImpl)modifiableRootModel,
                                                                                           (ProjectRootManagerImpl)ProjectRootManager
                                                                                             .getInstance(myProject),
                                                                                           VirtualFilePointerManager.getInstance()));
                }
              }

              VirtualFile content = LocalFileSystem.getInstance().findFileByPath(component.getPath());
              if (content == null) {
                content = LocalFileSystem.getInstance().refreshAndFindFileByPath(component.getPath());
              }
              modifiableRootModel.addContentEntry(content);
            }

            @Override
            public ModuleType getModuleType() {
              return ModuleType.get(rootModel.getModule());
            }
          };
          builder.setName(component.getNameValue());
          builder.setModuleFilePath(path + "/" + builder.getName() + ModuleFileType.DOT_DEFAULT_EXTENSION);
          final Module module = myContext.myModulesConfigurator.addModule(builder);
          if (module != null) {
            addModuleNode(module);
          }
        }
        catch (Exception e1) {
          LOG.error(e1);
        }
      }
      else {
        copyByExtension(namedConfigurable);
      }
    }

    @Override
    public void update(final AnActionEvent e) {
      TreePath[] selectionPaths = myTree.getSelectionPaths();
      if (selectionPaths == null || selectionPaths.length != 1) {
        e.getPresentation().setEnabled(false);
      }
      else {
        final NamedConfigurable selectedConfigurable = getSelectedConfigurable();
        e.getPresentation().setEnabled(selectedConfigurable instanceof ModuleConfigurable || canBeCopiedByExtension(selectedConfigurable));
      }
    }
  }

  private class AddModuleAction extends AnAction implements DumbAware {

    private final boolean myImport;

    public AddModuleAction(boolean anImport) {
      super(ProjectBundle.message("add.new.module.text.full"), null, AllIcons.Actions.Module);
      myImport = anImport;
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      addModule(myImport);
    }
  }

  private static class MergingComparator<T> implements Comparator<T> {
    private final List<Comparator<T>> myDelegates;

    public MergingComparator(final List<Comparator<T>> delegates) {
      myDelegates = delegates;
    }

    @Override
    public int compare(final T o1, final T o2) {
      for (Comparator<T> delegate : myDelegates) {
        int value = delegate.compare(o1, o2);
        if (value != 0) return value;
      }
      return 0;
    }
  }
}
