// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.CommonBundle;
import com.intellij.facet.Facet;
import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.facet.impl.ui.actions.AddFacetToModuleAction;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.impl.FlattenModulesToggleAction;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.ModuleGroupingImplementation;
import com.intellij.ide.projectView.impl.ModuleGroupingTreeHelper;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.NamePathComponent;
import com.intellij.ide.util.projectWizard.ProjectWizardUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.*;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ClonableOrderEntry;
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.actions.ChangeModuleNamesAction;
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
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.ui.navigation.Place;
import com.intellij.util.PlatformIcons;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;

public class ModuleStructureConfigurable extends BaseStructureConfigurable implements Place.Navigator, Configurable.WithEpDependencies {
  private static final Comparator<MyNode> NODE_COMPARATOR = (o1, o2) -> {
    final NamedConfigurable<?> configurable1 = o1.getConfigurable();
    final NamedConfigurable<?> configurable2 = o2.getConfigurable();
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

  private final FacetEditorFacadeImpl myFacetEditorFacade;

  private final List<RemoveConfigurableHandler<?>> myRemoveHandlers;

  public ModuleStructureConfigurable(ProjectStructureConfigurable projectStructureConfigurable) {
    super(projectStructureConfigurable);
    myFacetEditorFacade = new FacetEditorFacadeImpl(myProjectStructureConfigurable, TREE_UPDATER);
    myModuleManager = ModuleManager.getInstance(myProject);
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

  @NotNull
  @Override
  protected String getTextForSpeedSearch(MyNode node) {
    if (node instanceof ModuleNode) {
      return ((ModuleNode)node).getFullModuleName();
    }
    else if (node instanceof ModuleGroupNodeImpl) {
      return ((ModuleGroupNodeImpl)node).getModuleGroup().getQualifiedName();
    }
    else {
      return super.getTextForSpeedSearch(node);
    }
  }

  @Override
  protected ArrayList<AnAction> getAdditionalActions() {
    final ArrayList<AnAction> result = new ArrayList<>();
    result.add(ActionManager.getInstance().getAction(IdeActions.GROUP_MOVE_MODULE_TO_GROUP));
    result.add(new ChangeModuleNamesAction());
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
      result.add(new FlattenModulesToggleAction(myProject, () -> true, () -> myFlattenModules, value -> {
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
    myProjectStructureConfigurable.getFacetStructureConfigurable().disposeMultipleSettingsEditor();
    ThreadingAssertions.assertEventDispatchThread();
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
  protected boolean updateMultiSelection(final List<? extends NamedConfigurable> selectedConfigurables) {
    return myProjectStructureConfigurable.getFacetStructureConfigurable().updateMultiSelection(selectedConfigurables, getDetailsComponent());
  }

  @Override
  public @NotNull Collection<BaseExtensionPointName<?>> getDependencies() {
    return Collections.singletonList(ModuleStructureExtension.EP_NAME);
  }

  private void updateModuleEditorSelection(final NamedConfigurable configurable) {
    if (configurable instanceof ModuleConfigurable moduleConfigurable) {
      final ModuleEditor editor = moduleConfigurable.getModuleEditor();
      if (editor != null) { //already deleted
        editor.init(myHistory);
      }
    }
    if (configurable instanceof FacetConfigurable facetConfigurable) {
      facetConfigurable.getEditor().onFacetSelected();
    }
  }


  private void createProjectNodes() {
    ModuleGrouper moduleGrouper = getModuleGrouper();
    ModuleGroupingTreeHelper<Module, MyNode> helper = ModuleGroupingTreeHelper.forEmptyTree(!myHideModuleGroups && !myFlattenModules,
                                                                                    ModuleGroupingTreeHelper.createDefaultGrouping(moduleGrouper),
                                                                                    ModuleStructureConfigurable::createModuleGroupNode,
                                                                                    m -> createModuleNode(m, moduleGrouper), getNodeComparator());
    var modules = Arrays.stream(myModuleManager.getModules()).filter(module -> ModuleStructureFilterExtension.isAllowed(module)).toList();
    helper.createModuleNodes(modules, myRoot, getTreeModel());
    if (containsSecondLevelNodes(myRoot)) {
      myTree.setShowsRootHandles(true);
    }
    sortDescendants(myRoot);
    if (myProject.isDefault()) {  //do not add modules node in case of template project
      myRoot.removeAllChildren();
    }

    addRootNodesFromExtensions(myRoot, myProject);
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
    final NamedConfigurable<?> moduleGroupConfigurable = new TextConfigurable<>(moduleGroup, moduleGroup.toString(),
                                                                                JavaUiBundle.message("module.group.banner.text",
                                                                                                      moduleGroup.toString()),
                                                                                JavaUiBundle.message("project.roots.module.groups.text"),
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
    Set<MyNode> nodeSet = new HashSet<>();
    for (Module module : modules) {
      MyNode node = findModuleNode(module);
      LOG.assertTrue(node != null, "Module " + module.getName() + " is not in project.");
      nodes.add(Pair.create(node, module));
      nodeSet.add(node);
    }
    ModuleGroupingTreeHelper<Module, MyNode> helper = createGroupingHelper(nodeSet::contains);
    helper.moveModuleNodesToProperGroup(nodes, myRoot, getTreeModel(), myTree);
    return true;
  }

  private DefaultTreeModel getTreeModel() {
    return (DefaultTreeModel)myTree.getModel();
  }

  @NotNull
  private ModuleGroupingTreeHelper<Module, MyNode> createGroupingHelper(Predicate<? super MyNode> nodeToBeMovedFilter) {
    ModuleGrouper grouper = getModuleGrouper();
    ModuleGroupingImplementation<Module> grouping = ModuleGroupingTreeHelper.createDefaultGrouping(grouper);
    return ModuleGroupingTreeHelper.forTree(myRoot, node -> node instanceof ModuleGroupNode ? ((ModuleGroupNode)node).getModuleGroup() : null,
                                            node -> node instanceof ModuleNode ? ((ModuleNode)node).getModule() : null,
                                            !myHideModuleGroups && !myFlattenModules,
                                            grouping, ModuleStructureConfigurable::createModuleGroupNode,
                                            module -> createModuleNode(module, grouper), getNodeComparator(),
                                            node -> nodeToBeMovedFilter.test(node));
  }

  @Override
  protected Comparator<MyNode> getNodeComparator() {
    List<Comparator<MyNode>> comparators = ContainerUtil
      .mapNotNull(ModuleStructureExtension.EP_NAME.getExtensions(), ModuleStructureExtension::getNodeComparator);
    return new MergingComparator<>(ContainerUtil.concat(comparators, Collections.singletonList(NODE_COMPARATOR)));
  }

  @Override
  public void init(final StructureConfigurableContext context) {
    super.init(context);

    addItemsChangeListener(new ItemsChangeListener() {
      @Override
      public void itemChanged(@Nullable Object deletedItem) {
        if (deletedItem instanceof Library library) {
          final MyNode node = findNodeByObject(myRoot, library);
          if (node != null) {
            final TreeNode parent = node.getParent();
            node.removeFromParent();
            getTreeModel().reload(parent);
          }
          myContext.getDaemonAnalyzer().removeElement(new LibraryProjectStructureElement(myContext, library));
        }
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
    checkForEmptyAndDuplicatedNames(JavaUiBundle.message("rename.message.prefix.module"),
                                    JavaUiBundle.message("rename.module.title"), ModuleConfigurable.class);

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
    return UiDataProvider.wrapComponent(super.createComponent(), sink -> {
      sink.set(LangDataKeys.MODULE_CONTEXT_ARRAY, getModuleContexts());
      sink.set(LangDataKeys.MODULE_CONTEXT, getSelectedModule());
      sink.set(LangDataKeys.MODIFIABLE_MODULE_MODEL, myContext.myModulesConfigurator.getModuleModel());
      sink.set(PlatformCoreDataKeys.SELECTED_ITEM, getSelectedObject());
    });
  }

  @Override
  public String getDisplayName() {
    return JavaUiBundle.message("project.roots.display.name");
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
      p.putPath(ModuleEditor.SELECTED_EDITOR_NAME, ClasspathEditor.getName());
      r = () -> {
        if (orderEntry != null) {
          ModuleEditor moduleEditor = ((ModuleConfigurable)node.getConfigurable()).getModuleEditor();
          ModuleConfigurationEditor editor = moduleEditor.getEditor(ClasspathEditor.getName());
          if (editor instanceof ClasspathEditor) {
            ((ClasspathEditor)editor).selectOrderEntry(orderEntry);
          }
        }
      };
    }
    final ActionCallback result = myProjectStructureConfigurable.navigateTo(p, true);
    return r != null ? result.doWhenDone(r) : result;
  }

  private ModuleGrouper getModuleGrouper() {
    return ModuleGrouper.instanceFor(myProject, myContext.myModulesConfigurator.getModuleModel());
  }

  /**
   * @deprecated use {@link ProjectStructureConfigurable#getModulesConfig()} instead
   */
  @Deprecated(forRemoval = true)
  public static ModuleStructureConfigurable getInstance(final Project project) {
    return ProjectStructureConfigurable.getInstance(project).getModulesConfig();
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
                                     JavaUiBundle.message("project.roots.replace.library.entry.message", entry.getPresentableName()),
                                     JavaUiBundle.message("project.roots.replace.library.entry.title"),
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

  private void addModule(boolean anImport, boolean detectModuleBase) {
    final List<Module> modules;
    if (anImport) {
      modules = myContext.myModulesConfigurator.addImportModule(myTree);
    } else {
      // If the user creates a module when selecting an existing module in the project structure dialog,
      //   they may expect that the new module "will be located under the selected one".
      // This is not completely correct from the project model point of view, as the modules cannot be located under each other,
      //   but from the user perspective it makes sense.
      //
      // So, here we take the first content root of the selected module and use it as the base path for the new module.
      // In the majority of cases, there is only one content root, so the new module will be located there.
      // If there are multiple content roots, it still makes sense to place the new module under some of them.
      //
      // The base path is detected only if the add action was executed from the tree context action. If it was executed from the "plus"
      //   button, we don't detect the module base.
      String basePath = null;
      if (detectModuleBase) {
        Module selectedModule = getSelectedModule();
        if (selectedModule != null) {
          VirtualFile file = Arrays.stream(ModuleRootManager.getInstance(selectedModule).getContentRoots()).findFirst().orElse(null);
          if (file != null) {
            basePath = file.getPath();
          }
        }
      }
      modules = myContext.myModulesConfigurator.addNewModule(basePath);
    }
    if (modules != null && !modules.isEmpty()) {
      //new module wizard may add yet another SDK to the project
      myProjectStructureConfigurable.getProjectJdksModel().syncSdks();
      for (Module module : modules) {
        addModuleNode(module);
      }
    }
  }

  private void addModuleNode(@NotNull Module module) {
    final TreePath selectionPath = myTree.getSelectionPath();
    MyNode parent = null;
    if (selectionPath != null) {
      MyNode selected = (MyNode)selectionPath.getLastPathComponent();
      final Object o = selected.getConfigurable().getEditableObject();
      if (o instanceof ModuleGroup) {
        if (!ModuleGrouperKt.isQualifiedModuleNamesEnabled(module.getProject())) {
          myContext.myModulesConfigurator.getModuleModel().setModuleGroupPath(module, ((ModuleGroup)o).getGroupPath());
        }
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
    TreeUtil.insertNode(node, parent, getTreeModel(), getNodeComparator());
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
    return myContext != null && myContext.myModulesConfigurator != null
           ? myContext.myModulesConfigurator.getModule(moduleName)
           : myModuleManager.findModuleByName(moduleName);
  }

  public StructureConfigurableContext getContext() {
    return myContext;
  }

  private static boolean canBeCopiedByExtension(final NamedConfigurable<?> configurable) {
    for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
      if (extension.canBeCopied(configurable)) {
        return true;
      }
    }
    return false;
  }

  private void copyByExtension(final NamedConfigurable<?> configurable) {
    for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
      extension.copy(configurable, TREE_UPDATER);
    }
  }

  private class ModuleNode extends MyNode implements ModuleGroupNode {
    private final ModuleGroup myModuleAsGroup;

    ModuleNode(@NotNull ModuleConfigurable configurable, @Nullable ModuleGroup moduleAsGroup) {
      super(configurable);
      myModuleAsGroup = moduleAsGroup;
    }

    @Override
    public ModuleGroup getModuleGroup() {
      return myModuleAsGroup;
    }

    @NotNull
    @Override
    public String getDisplayName() {
      if (myFlattenModules) {
        return getFullModuleName();
      }
      String parentGroupName = null;
      if (parent instanceof ModuleGroupNode) {
        ModuleGroup group = ((ModuleGroupNode)parent).getModuleGroup();
        if (group != null) {
          parentGroupName = group.getQualifiedName();
        }
      }
      return getModuleGrouper().getShortenedName(getModule(), parentGroupName);
    }

    @NotNull
    private String getFullModuleName() {
      return myContext.myModulesConfigurator.getModuleModel().getActualName(getModule());
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
        ModuleGroupingTreeHelper<Module, MyNode> helper = createGroupingHelper(Predicate.isEqual(this));
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

    ModuleGroupNodeImpl(@NotNull NamedConfigurable configurable, @NotNull ModuleGroup moduleGroup) {
      super(configurable, true);
      myModuleGroup = moduleGroup;
    }

    @NotNull
    @Override
    public String getDisplayName() {
      List<String> parentGroupPath;
      if (parent instanceof ModuleGroupNode) {
        ModuleGroup parentGroup = ((ModuleGroupNode)parent).getModuleGroup();
        parentGroupPath = parentGroup != null ? parentGroup.getGroupPathList() : Collections.emptyList();
      }
      else {
        parentGroupPath = Collections.emptyList();
      }
      List<String> groupPath = myModuleGroup.getGroupPathList();
      if (ContainerUtil.startsWith(groupPath, parentGroupPath)) {
        return StringUtil.join(groupPath.subList(parentGroupPath.size(), groupPath.size()), ".");
      }
      else {
        return StringUtil.join(groupPath, ".");
      }
    }

    @Override
    @NotNull
    public ModuleGroup getModuleGroup() {
      return myModuleGroup;
    }
  }

  private class FacetInModuleRemoveHandler extends RemoveConfigurableHandler<Facet> {
    FacetInModuleRemoveHandler() {
      super(FacetConfigurable.class);
    }

    @Override
    public boolean remove(@NotNull Collection<? extends Facet> facets) {
      for (Facet<?> facet : facets) {
        List<Facet> removed = myContext.myModulesConfigurator.getFacetsConfigurator().removeFacet(facet);
        myProjectStructureConfigurable.getFacetStructureConfigurable().removeFacetNodes(removed);
      }
      return true;
    }
  }

  private class ModuleRemoveHandler extends RemoveConfigurableHandler<Module> {
    ModuleRemoveHandler() {
      super(ModuleConfigurable.class);
    }

    @Override
    public boolean remove(@NotNull Collection<? extends Module> modules) {
      ModulesConfigurator modulesConfigurator = myContext.myModulesConfigurator;
      List<ModuleEditor> moduleEditors = ContainerUtil.mapNotNull(modules, modulesConfigurator::getModuleEditor);
      if (moduleEditors.isEmpty()) return false;
      if (!modulesConfigurator.canDeleteModules(moduleEditors)) return false;

      List<Module> modulesToDelete = ContainerUtil.mapNotNull(moduleEditors, ModuleEditor::getModule);
      for (Module module : modulesToDelete) {
        List<Facet> removed = modulesConfigurator.getFacetsConfigurator().removeAllFacets(module);
        myProjectStructureConfigurable.getFacetStructureConfigurable().removeFacetNodes(removed);
      }
      modulesConfigurator.deleteModules(moduleEditors);
      for (Module module : modulesToDelete) {
        myContext.getDaemonAnalyzer().removeElement(new ModuleProjectStructureElement(myContext, module));

        for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
          extension.moduleRemoved(module);
        }
      }
      return true;
    }
  }

  private Module @Nullable [] getModuleContexts() {
    final TreePath[] paths = myTree.getSelectionPaths();
    Set<Module> modules = new LinkedHashSet<>();
    if (paths != null) {
      for (TreePath path : paths) {
        MyNode node = (MyNode)path.getLastPathComponent();
        final NamedConfigurable<?> configurable = node.getConfigurable();
        LOG.assertTrue(configurable != null, "already disposed");
        final Object o = configurable.getEditableObject();
        if (o instanceof Module) {
          modules.add((Module)o);
        }
        else if (node instanceof ModuleGroupNode && ((ModuleGroupNode)node).getModuleGroup() != null) {
          TreeUtil.treeNodeTraverser(node).forEach(descendant -> {
            if (descendant instanceof MyNode) {
              Object object = ((MyNode)descendant).getConfigurable().getEditableObject();
              if (object instanceof Module) {
                modules.add((Module)object);
              }
            }
          });
        }
      }
    }
    return !modules.isEmpty() ? modules.toArray(Module.EMPTY_ARRAY) : null;
  }

  private class HideGroupsAction extends ToggleAction implements DumbAware {
    HideGroupsAction() {
      super("", "", AllIcons.ObjectBrowser.CompactEmptyPackages);
    }

    @Override
    public void update(@NotNull
                       final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      String text = JavaUiBundle
        .message(myHideModuleGroups ? "project.roots.plain.mode.action.text.enabled" : "project.roots.plain.mode.action.text.disabled");
      presentation.setText(text);
      presentation.setDescription(text);

      if (myContext.myModulesConfigurator != null) {
        presentation.setVisible(myContext.myModulesConfigurator.getModuleModel().hasModuleGroups());
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myHideModuleGroups;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
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
    createGroupingHelper(node -> true).moveAllModuleNodesToProperGroups(myRoot, getTreeModel());
    if (selection != null) {
      TreeUtil.selectInTree(selection, true, myTree);
    }
  }

  @Override
  protected AbstractAddGroup createAddAction(boolean fromPopup) {
    return new AbstractAddGroup(JavaUiBundle.message("add.new.header.text")) {
      @Override
      public AnAction @NotNull [] getChildren(@Nullable
                                    final AnActionEvent e) {

        AnAction addModuleAction = new AddModuleAction(false, fromPopup);
        addModuleAction.getTemplatePresentation().setText(JavaUiBundle.message("action.text.new.module"));
        List<AnAction> result = new ArrayList<>();
        result.add(addModuleAction);

        AnAction importModuleAction = new AddModuleAction(true, fromPopup);
        importModuleAction.getTemplatePresentation().setText(JavaUiBundle.message("action.text.import.module"));
        importModuleAction.getTemplatePresentation().setIcon(AllIcons.ToolbarDecorator.Import);
        result.add(importModuleAction);

        final Collection<AnAction> actions = AddFacetToModuleAction.createAddFrameworkActions(myFacetEditorFacade, myProject);
        if (!actions.isEmpty()) {
          result.add(new Separator(JavaUiBundle.message("add.group.framework.separator")));
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
        return result.toArray(AnAction.EMPTY_ARRAY);
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
    return JavaUiBundle.message("empty.module.selection.string");
  }

  private final class MyCopyAction extends AnAction implements DumbAware {
    private MyCopyAction() {
      super(CommonBundle.messagePointer("button.copy"), CommonBundle.messagePointer("button.copy"), COPY_ICON);
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
      NamedConfigurable<?> namedConfigurable = getSelectedConfigurable();
      if (!(namedConfigurable instanceof ModuleConfigurable)) {
        copyByExtension(namedConfigurable);
      }

      try {
        ModuleEditor moduleEditor = ((ModuleConfigurable)namedConfigurable).getModuleEditor();
        String modulePresentation = IdeCoreBundle.message("project.new.wizard.module.identification");
        NamePathComponent component = new NamePathComponent(JavaUiBundle.message("label.module.name"),
                                                            JavaUiBundle.message("label.component.file.location", StringUtil.capitalize(modulePresentation)),
                                                            JavaUiBundle
                                                              .message("title.select.project.file.directory", modulePresentation),
                                                            JavaUiBundle.message("description.select.project.file.directory",
                                                                                 StringUtil.capitalize(modulePresentation)),
                                                            true,
                                                            false);
        Module originalModule = moduleEditor.getModule();
        if (originalModule != null) {
          component.setPath(FileUtil.toSystemDependentName(originalModule.getModuleNioFile().getParent().toString()));
        }

        DialogBuilder dialogBuilder = new DialogBuilder(myTree);
        dialogBuilder.setTitle(JavaUiBundle.message("copy.module.dialog.title"));
        dialogBuilder.setCenterPanel(component);
        dialogBuilder.setPreferredFocusComponent(component.getNameComponent());
        dialogBuilder.setOkOperation(() -> {
          final String name = component.getNameValue();
          if (name.isEmpty()) {
            Messages.showErrorDialog(JavaUiBundle.message("enter.module.copy.name.error.message"), CommonBundle.getErrorTitle());
            return;
          }
          if (getModule(name) != null) {
            Messages
              .showErrorDialog(JavaUiBundle.message("module.0.already.exists.error.message", name), CommonBundle.getErrorTitle());
            return;
          }

          if (component.getPath().isEmpty()) {
            Messages.showErrorDialog(JavaUiBundle.message("prompt.enter.project.file.location", modulePresentation),
                                     CommonBundle.getErrorTitle());
            return;
          }
          if (!ProjectWizardUtil
            .createDirectoryIfNotExists(JavaUiBundle.message("directory.project.file.directory", modulePresentation), component.getPath(),
                                        true)) {
            Messages.showErrorDialog(JavaUiBundle.message("path.0.is.invalid.error.message", component.getPath()),
                                     CommonBundle.getErrorTitle());
            return;
          }
          dialogBuilder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
        });
        if (dialogBuilder.show() != DialogWrapper.OK_EXIT_CODE) {
          return;
        }

        ModifiableRootModel rootModel = moduleEditor.getModifiableRootModel();
        Path path = Paths.get(component.getPath());
        ModuleBuilder builder = new CopiedModuleBuilder(rootModel, path, myProject);
        builder.setName(component.getNameValue());
        builder.setModuleFilePath(path.resolve(builder.getName() + ModuleFileType.DOT_DEFAULT_EXTENSION).toString());
        Module module = myContext.myModulesConfigurator.addModule(builder);
        if (module != null) {
          addModuleNode(module);
        }
      }
      catch (Exception e1) {
        LOG.error(e1);
      }
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      TreePath[] selectionPaths = myTree.getSelectionPaths();
      if (selectionPaths == null || selectionPaths.length != 1) {
        e.getPresentation().setEnabled(false);
      }
      else {
        final NamedConfigurable<?> selectedConfigurable = getSelectedConfigurable();
        e.getPresentation().setEnabled(selectedConfigurable instanceof ModuleConfigurable || canBeCopiedByExtension(selectedConfigurable));
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  static final class CopiedModuleBuilder extends ModuleBuilder {
    @NotNull private final ModifiableRootModel myRootModel;
    @NotNull private final Path myComponentPath;
    @NotNull private final Project myProject;

    CopiedModuleBuilder(@NotNull ModifiableRootModel rootModel, @NotNull Path componentPath, @NotNull Project project) {
      this.myRootModel = rootModel;
      this.myComponentPath = componentPath;
      this.myProject = project;
    }

    @Override
    public void setupRootModel(@NotNull ModifiableRootModel modifiableRootModel) {
      if (myRootModel.isSdkInherited()) {
        modifiableRootModel.inheritSdk();
      }
      else {
        modifiableRootModel.setSdk(myRootModel.getSdk());
      }

      modifiableRootModel.getModuleExtension(CompilerModuleExtension.class).inheritCompilerOutputPath(true);

      modifiableRootModel.getModuleExtension(LanguageLevelModuleExtension.class)
        .setLanguageLevel(LanguageLevelUtil.getCustomLanguageLevel(myRootModel.getModule()));

      for (OrderEntry entry : myRootModel.getOrderEntries()) {
        if (entry instanceof JdkOrderEntry) continue;
        if (entry instanceof ModuleSourceOrderEntry) continue;
        if (entry instanceof ClonableOrderEntry) {
          modifiableRootModel.addOrderEntry(((ClonableOrderEntry)entry).cloneEntry(modifiableRootModel,
                                                                                   (ProjectRootManagerImpl)ProjectRootManager.getInstance(myProject),
                                                                                   VirtualFilePointerManager.getInstance()));
        }
      }

      VirtualFile content = LocalFileSystem.getInstance().findFileByNioFile(myComponentPath);
      if (content == null) {
        try {
          Files.createFile(NioFiles.createParentDirectories(myComponentPath));
        }
        catch (IOException e) {
          throw new UncheckedIOException(e);
        }
        content = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(myComponentPath);
      }
      modifiableRootModel.addContentEntry(content);
    }

    @Override
    public ModuleType<?> getModuleType() {
      return ModuleType.get(myRootModel.getModule());
    }
  }

  private final class AddModuleAction extends AnAction implements DumbAware {
    private final boolean myImport;
    private final boolean myDetectModuleBase;

    AddModuleAction(boolean anImport, boolean detectModuleBase) {
      super(JavaUiBundle.message("add.new.module.text.full"), null, AllIcons.Nodes.Module);
      myImport = anImport;
      myDetectModuleBase = detectModuleBase;
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
      addModule(myImport, myDetectModuleBase);
    }
  }

  private static class MergingComparator<T> implements Comparator<T> {
    private final List<? extends Comparator<T>> myDelegates;

    MergingComparator(final List<? extends Comparator<T>> delegates) {
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
