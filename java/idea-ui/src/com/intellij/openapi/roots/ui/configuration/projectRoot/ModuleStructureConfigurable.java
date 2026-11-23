// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.ide.projectView.impl.ModuleGroupingTreeHelper;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.NamePathComponent;
import com.intellij.ide.util.projectWizard.ProjectWizardUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleGrouper;
import com.intellij.openapi.module.ModuleGrouperKt;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleSourceOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.ClonableOrderEntry;
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.actions.ChangeModuleNamesAction;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.LibraryProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ModuleProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Predicates;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.platform.eel.provider.EelProviderUtil;
import com.intellij.ui.navigation.Place;
import com.intellij.util.PlatformIcons;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.JComponent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class ModuleStructureConfigurable extends BaseStructureConfigurable implements Place.Navigator, Configurable.WithEpDependencies {
  @ApiStatus.Internal
  @VisibleForTesting
  public static final Comparator<MyNode> NODE_COMPARATOR = (o1, o2) -> {
    var editableObject1 = o1.getConfigurable().getEditableObject();
    var editableObject2 = o2.getConfigurable().getEditableObject();
    var rankComparison = Integer.compare(getSortRank(editableObject1), getSortRank(editableObject2));
    return rankComparison != 0 ? rankComparison : StringUtil.naturalCompare(o1.getDisplayName(), o2.getDisplayName());
  };

  private static int getSortRank(Object editableObject) {
    return switch (editableObject) {
      case ModuleGroup _ -> 0;
      case Module _ -> 1;
      case Facet<?> _ -> 2;
      case String _ -> 3;
      case null, default -> 4;
    };
  }

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
    for (var extension : ModuleStructureExtension.EP_NAME.getExtensionList()) {
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
  protected @NotNull String getTextForSpeedSearch(@NotNull MyNode node) {
    return switch (node) {
      case ModuleNode moduleNode -> moduleNode.getFullModuleName();
      case ModuleGroupNodeImpl groupNode -> groupNode.getModuleGroup().getQualifiedName();
      default -> super.getTextForSpeedSearch(node);
    };
  }

  @Override
  protected ArrayList<AnAction> getAdditionalActions() {
    var result = new ArrayList<AnAction>();
    result.add(ActionManager.getInstance().getAction(IdeActions.GROUP_MOVE_MODULE_TO_GROUP));
    result.add(new ChangeModuleNamesAction());
    return result;
  }

  @Override
  public void addNode(MyNode nodeToAdd, MyNode parent) {
    super.addNode(nodeToAdd, parent);
  }

  @Override
  protected @NotNull ArrayList<AnAction> createActions(boolean fromPopup) {
    var result = super.createActions(fromPopup);
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
  protected @NotNull List<? extends AnAction> createCopyActions(boolean fromPopup) {
    return List.of(new MyCopyAction());
  }

  @Override
  protected void loadTree() {
    createProjectNodes();
    getTreeModel().reload();
    myUiDisposed = false;
  }

  @Override
  protected @NotNull Collection<? extends ProjectStructureElement> getProjectStructureElements() {
    var result = new ArrayList<ProjectStructureElement>();
    for (var module : myModuleManager.getModules()) {
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
  protected boolean updateMultiSelection(List<? extends NamedConfigurable> selectedConfigurables) {
    return myProjectStructureConfigurable.getFacetStructureConfigurable().updateMultiSelection(selectedConfigurables, getDetailsComponent());
  }

  @Override
  public @NotNull Collection<BaseExtensionPointName<?>> getDependencies() {
    return List.of(ModuleStructureExtension.EP_NAME);
  }

  private void updateModuleEditorSelection(NamedConfigurable<?> configurable) {
    if (configurable instanceof ModuleConfigurable moduleConfigurable) {
      var editor = moduleConfigurable.getModuleEditor();
      if (editor != null) { //already deleted
        editor.init(myHistory);
      }
    }
    if (configurable instanceof FacetConfigurable facetConfigurable) {
      facetConfigurable.getEditor().onFacetSelected();
    }
  }

  private void createProjectNodes() {
    var moduleGrouper = getModuleGrouper();
    var helper = ModuleGroupingTreeHelper.forEmptyTree(
      !myHideModuleGroups && !myFlattenModules,
      ModuleGroupingTreeHelper.createDefaultGrouping(moduleGrouper),
      ModuleStructureConfigurable::createModuleGroupNode,
      m -> createModuleNode(m, moduleGrouper), getNodeComparator()
    );
    var modules = Arrays.stream(myModuleManager.getModules()).toList();
    helper.createModuleNodes(modules, myRoot, getTreeModel());
    if (containsSecondLevelNodes(myRoot)) {
      myTree.setShowsRootHandles(true);
    }
    sortDescendants(myRoot);
    if (myProject.isDefault()) {  //do not add modules node in case of template project
      myRoot.removeAllChildren();
    }
  }

  private static boolean containsSecondLevelNodes(TreeNode rootNode) {
    var count = rootNode.getChildCount();
    for (var i = 0; i < count; i++) {
      var child = rootNode.getChildAt(i);
      if (child.getChildCount() > 0) {
        return true;
      }
    }
    return false;
  }

  private @NotNull ModuleNode createModuleNode(Module module, ModuleGrouper moduleGrouper) {
    var configurable = new ModuleConfigurable(myContext.myModulesConfigurator, module, TREE_UPDATER, moduleGrouper);
    var groupPath = moduleGrouper.getModuleAsGroupPath(module);
    var node = new ModuleNode(configurable, groupPath != null ? new ModuleGroup(groupPath) : null);
    myFacetEditorFacade.addFacetsNodes(module, node);
    addNodesFromExtensions(module, node);
    return node;
  }

  private static @NotNull MyNode createModuleGroupNode(ModuleGroup moduleGroup) {
    var moduleGroupConfigurable = new TextConfigurable<>(
      moduleGroup,
      moduleGroup.toString(),
      JavaUiBundle.message("module.group.banner.text", moduleGroup.toString()),
      JavaUiBundle.message("project.roots.module.groups.text"),
      PlatformIcons.CLOSED_MODULE_GROUP_ICON
    );
    return new ModuleGroupNodeImpl(moduleGroupConfigurable, moduleGroup);
  }

  private void addNodesFromExtensions(Module module, MyNode moduleNode) {
    for (var extension : ModuleStructureExtension.EP_NAME.getExtensionList()) {
      extension.addModuleNodeChildren(module, moduleNode, TREE_UPDATER);
    }
  }

  public boolean updateProjectTree(Module[] modules) {
    if (myRoot.getChildCount() == 0) return false; //isn't visible
    var nodes = new ArrayList<Pair<MyNode, Module>>(modules.length);
    var nodeSet = new HashSet<MyNode>();
    for (var module : modules) {
      var node = findModuleNode(module);
      LOG.assertTrue(node != null, "Module " + module.getName() + " is not in project.");
      nodes.add(Pair.create(node, module));
      nodeSet.add(node);
    }
    var helper = createGroupingHelper(nodeSet::contains);
    helper.moveModuleNodesToProperGroup(nodes, myRoot, getTreeModel(), myTree);
    return true;
  }

  private DefaultTreeModel getTreeModel() {
    return (DefaultTreeModel)myTree.getModel();
  }

  private ModuleGroupingTreeHelper<Module, MyNode> createGroupingHelper(Predicate<MyNode> nodeToBeMovedFilter) {
    var grouper = getModuleGrouper();
    var grouping = ModuleGroupingTreeHelper.createDefaultGrouping(grouper);
    return ModuleGroupingTreeHelper.forTree(
      myRoot,
      node -> node instanceof ModuleGroupNode groupNode ? groupNode.getModuleGroup() : null,
      node -> node instanceof ModuleNode moduleNode ? moduleNode.getModule() : null,
      !myHideModuleGroups && !myFlattenModules,
      grouping,
      ModuleStructureConfigurable::createModuleGroupNode,
      module -> createModuleNode(module, grouper), getNodeComparator(),
      node -> nodeToBeMovedFilter.test(node)
    );
  }

  @Override
  protected Comparator<MyNode> getNodeComparator() {
    var comparators = ContainerUtil.mapNotNull(ModuleStructureExtension.EP_NAME.getExtensionList(), ModuleStructureExtension::getNodeComparator);
    return new MergingComparator<>(ContainerUtil.concat(comparators, List.of(NODE_COMPARATOR)));
  }

  @Override
  public void init(StructureConfigurableContext context) {
    super.init(context);

    addItemsChangeListener(new ItemsChangeListener() {
      @Override
      public void itemChanged(@Nullable Object deletedItem) {
        if (deletedItem instanceof Library library) {
          var node = findNodeByObject(myRoot, library);
          if (node != null) {
            var parent = node.getParent();
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
    for (var extension : ModuleStructureExtension.EP_NAME.getExtensionList()) {
      extension.reset(myProject);
    }
  }

  @Override
  public void apply() throws ConfigurationException {
    checkForEmptyAndDuplicatedNames(
      JavaUiBundle.message("rename.message.prefix.module"), JavaUiBundle.message("rename.module.title"), ModuleConfigurable.class
    );

    // let's apply extensions first, since they can write to/commit modifiable models
    for (var extension : ModuleStructureExtension.EP_NAME.getExtensionList()) {
      if (extension.isModified()) {
        extension.apply();
      }
    }

    if (myContext.myModulesConfigurator.isModified()) {
      myContext.myModulesConfigurator.apply();
    }

    for (var extension : ModuleStructureExtension.EP_NAME.getExtensionList()) {
      extension.afterModelCommit();
    }
  }

  @Override
  public boolean isModified() {
    if (myContext.myModulesConfigurator.isModified()) {
      return true;
    }

    for (var extension : ModuleStructureExtension.EP_NAME.getExtensionList()) {
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

    for (var extension : ModuleStructureExtension.EP_NAME.getExtensionList()) {
      extension.disposeUIResources();
    }
  }

  @Override
  public void dispose() {}

  @Override
  public @NotNull JComponent createComponent() {
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
  public @NotNull String getHelpTopic() {
    var topic = super.getHelpTopic();
    return topic != null ? topic : "reference.settingsdialog.project.structure.module";
  }

  public ActionCallback selectOrderEntry(@NotNull Module module, @Nullable OrderEntry orderEntry) {
    for (var extension : ModuleStructureExtension.EP_NAME.getExtensionList()) {
      var callback = extension.selectOrderEntry(module, orderEntry);
      if (callback != null) {
        return callback;
      }
    }

    var p = new Place();
    p.putPath(ProjectStructureConfigurable.CATEGORY, this);
    var r = (Runnable)null;

    var node = findModuleNode(module);
    if (node != null) {
      p.putPath(TREE_OBJECT, module);
      p.putPath(ModuleEditor.SELECTED_EDITOR_NAME, ClasspathEditor.getName());
      r = () -> {
        if (orderEntry != null) {
          var moduleEditor = ((ModuleConfigurable)node.getConfigurable()).getModuleEditor();
          var editor = moduleEditor.getEditor(ClasspathEditor.getName());
          if (editor instanceof ClasspathEditor classpathEditor) {
            classpathEditor.selectOrderEntry(orderEntry);
          }
        }
      };
    }
    var result = myProjectStructureConfigurable.navigateTo(p, true);
    return r != null ? result.doWhenDone(r) : result;
  }

  private ModuleGrouper getModuleGrouper() {
    return ModuleGrouper.instanceFor(myProject, myContext.myModulesConfigurator.getModuleModel());
  }

  public Project getProject() {
    return myProject;
  }

  public Module[] getModules() {
    if (myContext.myModulesConfigurator != null) {
      var model = myContext.myModulesConfigurator.getModuleModel();
      return model.getModules();
    }
    else {
      return myModuleManager.getModules();
    }
  }

  public void removeLibraryOrderEntry(Module module, Library library) {
    var moduleEditor = myContext.myModulesConfigurator.getModuleEditor(module);
    LOG.assertTrue(moduleEditor != null, "Current module editor was not initialized");
    var modelProxy = moduleEditor.getModifiableRootModelProxy();
    var entries = modelProxy.getOrderEntries();
    for (var entry : entries) {
      if (entry instanceof LibraryOrderEntry && Comparing.strEqual(entry.getPresentableName(), library.getName())) {
        modelProxy.removeOrderEntry(entry);
        break;
      }
    }

    myContext.getDaemonAnalyzer().queueUpdate(new ModuleProjectStructureElement(myContext, module));
    myTree.repaint();
  }

  public void addLibraryOrderEntry(Module module, Library library) {
    var moduleEditor = myContext.myModulesConfigurator.getModuleEditor(module);
    LOG.assertTrue(moduleEditor != null, "Current module editor was not initialized");
    var modelProxy = moduleEditor.getModifiableRootModelProxy();
    var entries = modelProxy.getOrderEntries();
    for (var entry : entries) {
      if (entry instanceof LibraryOrderEntry && Comparing.strEqual(entry.getPresentableName(), library.getName())) {
        var result = Messages.showYesNoDialog(
          module.getProject(),
          JavaUiBundle.message("project.roots.replace.library.entry.message", entry.getPresentableName()),
          JavaUiBundle.message("project.roots.replace.library.entry.title"),
          Messages.getInformationIcon()
        );
        if (result == Messages.YES) {
          modelProxy.removeOrderEntry(entry);
          break;
        }
      }
    }
    modelProxy.addLibraryEntry(library);
    myContext.getDaemonAnalyzer().queueUpdate(new ModuleProjectStructureElement(myContext, module));
    myTree.repaint();
  }

  public @Nullable MyNode findModuleNode(Module module) {
    return findNodeByObject(myRoot, module);
  }

  public FacetEditorFacadeImpl getFacetEditorFacade() {
    return myFacetEditorFacade;
  }

  public ProjectFacetsConfigurator getFacetConfigurator() {
    return myContext.myModulesConfigurator.getFacetsConfigurator();
  }

  private void addModule(boolean anImport, boolean detectModuleBase) {
    List<Module> modules;
    if (anImport) {
      modules = myContext.myModulesConfigurator.addImportModule(myTree);
    }
    else {
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
      var basePath = (String)null;
      if (detectModuleBase) {
        var selectedModule = getSelectedModule();
        if (selectedModule != null) {
          var file = Arrays.stream(ModuleRootManager.getInstance(selectedModule).getContentRoots()).findFirst().orElse(null);
          if (file != null) {
            basePath = file.getPath();
          }
        }
      }
      modules = myContext.myModulesConfigurator.addNewModule(basePath);
    }
    if (modules != null && !modules.isEmpty()) {
      //new module wizard may add yet another SDK to the project
      myProjectStructureConfigurable.getProjectJdksModel().syncSdks(EelProviderUtil.getEelMachine(myProject));
      for (var module : modules) {
        addModuleNode(module);
      }
    }
  }

  private void addModuleNode(Module module) {
    var selectionPath = myTree.getSelectionPath();
    var parent = (MyNode)null;
    if (selectionPath != null) {
      var selected = (MyNode)selectionPath.getLastPathComponent();
      var o = selected.getConfigurable().getEditableObject();
      if (o instanceof ModuleGroup group) {
        if (!ModuleGrouperKt.isQualifiedModuleNamesEnabled(module.getProject())) {
          //noinspection deprecation
          myContext.myModulesConfigurator.getModuleModel().setModuleGroupPath(module, group.getGroupPath());
        }
        parent = selected;
      }
      else if (o instanceof Module module1) { //create near selected
        var modifiableModuleModel = myContext.myModulesConfigurator.getModuleModel();
        var groupPath = modifiableModuleModel.getModuleGroupPath(module1);
        if (groupPath != null) {
          //noinspection deprecation
          modifiableModuleModel.setModuleGroupPath(module, groupPath);
          parent = findNodeByObject(myRoot, new ModuleGroup(Arrays.asList(groupPath)));
        }
      }
    }
    if (parent == null) parent = myRoot;
    var node = createModuleNode(module, getModuleGrouper());
    TreeUtil.insertNode(node, parent, getTreeModel(), getNodeComparator());
    selectNodeInTree(node);
    var daemonAnalyzer = myContext.getDaemonAnalyzer();
    daemonAnalyzer.queueUpdate(new ModuleProjectStructureElement(myContext, module));
    daemonAnalyzer.queueUpdateForAllElementsWithErrors(); //missing modules added
  }

  public @Nullable Module getSelectedModule() {
    var selectedObject = getSelectedObject();
    if (selectedObject instanceof Module module) {
      return module;
    }
    if (selectedObject instanceof Library library) {
      if (library.getTable() == null) {
        var selectionPath = myTree.getSelectionPath();
        if (selectionPath != null) {
          return (Module)((MyNode)((MyNode)selectionPath.getLastPathComponent()).getParent()).getConfigurable().getEditableObject();
        }
      }
    }
    return null;
  }

  @Override
  public @NotNull String getId() {
    return "project.structure";
  }

  public @Nullable Module getModule(String moduleName) {
    return (
      moduleName == null ? null :
      myContext != null && myContext.myModulesConfigurator != null ? myContext.myModulesConfigurator.getModule(moduleName) :
      myModuleManager.findModuleByName(moduleName)
    );
  }

  public StructureConfigurableContext getContext() {
    return myContext;
  }

  private static boolean canBeCopiedByExtension(NamedConfigurable<?> configurable) {
    for (var extension : ModuleStructureExtension.EP_NAME.getExtensionList()) {
      if (extension.canBeCopied(configurable)) {
        return true;
      }
    }
    return false;
  }

  private void copyByExtension(NamedConfigurable<?> configurable) {
    for (var extension : ModuleStructureExtension.EP_NAME.getExtensionList()) {
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

    @Override
    public @NotNull String getDisplayName() {
      if (myFlattenModules) {
        return getFullModuleName();
      }
      var parentGroupName = (String)null;
      if (parent instanceof ModuleGroupNode node) {
        var group = node.getModuleGroup();
        if (group != null) {
          parentGroupName = group.getQualifiedName();
        }
      }
      return getModuleGrouper().getShortenedName(getModule(), parentGroupName);
    }

    private @NotNull String getFullModuleName() {
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
      var autoScrollWasEnabled = myAutoScrollEnabled;
      try {
        myAutoScrollEnabled = false;
        var helper = createGroupingHelper(Predicate.isEqual(this));
        var newNode = helper.moveModuleNodeToProperGroup(this, getModule(), myRoot, treeModel, myTree);
        treeModel.reload(newNode);
      }
      finally {
        myAutoScrollEnabled = autoScrollWasEnabled;
      }
    }
  }

  private interface ModuleGroupNode extends MutableTreeNode {
    @Nullable ModuleGroup getModuleGroup();
  }

  private static final class ModuleGroupNodeImpl extends MyNode implements ModuleGroupNode {
    private final ModuleGroup myModuleGroup;

    private ModuleGroupNodeImpl(NamedConfigurable configurable, ModuleGroup moduleGroup) {
      super(configurable, true);
      myModuleGroup = moduleGroup;
    }

    @Override
    public @NotNull String getDisplayName() {
      List<String> parentGroupPath;
      if (parent instanceof ModuleGroupNode node) {
        var parentGroup = node.getModuleGroup();
        parentGroupPath = parentGroup != null ? parentGroup.getGroupPathList() : List.of();
      }
      else {
        parentGroupPath = List.of();
      }
      var groupPath = myModuleGroup.getGroupPathList();
      if (ContainerUtil.startsWith(groupPath, parentGroupPath)) {
        return StringUtil.join(groupPath.subList(parentGroupPath.size(), groupPath.size()), ".");
      }
      else {
        return StringUtil.join(groupPath, ".");
      }
    }

    @Override
    public @NotNull ModuleGroup getModuleGroup() {
      return myModuleGroup;
    }
  }

  @SuppressWarnings("rawtypes")
  private final class FacetInModuleRemoveHandler extends RemoveConfigurableHandler<Facet> {
    private FacetInModuleRemoveHandler() {
      super(FacetConfigurable.class);
    }

    @Override
    public boolean remove(@NotNull Collection<? extends Facet> facets) {
      for (var facet : facets) {
        var removed = myContext.myModulesConfigurator.getFacetsConfigurator().removeFacet(facet);
        myProjectStructureConfigurable.getFacetStructureConfigurable().removeFacetNodes(removed);
      }
      return true;
    }
  }

  private final class ModuleRemoveHandler extends RemoveConfigurableHandler<Module> {
    private ModuleRemoveHandler() {
      super(ModuleConfigurable.class);
    }

    @Override
    public boolean remove(@NotNull Collection<? extends Module> modules) {
      var modulesConfigurator = myContext.myModulesConfigurator;
      var moduleEditors = ContainerUtil.mapNotNull(modules, modulesConfigurator::getModuleEditor);
      if (moduleEditors.isEmpty()) return false;
      if (!modulesConfigurator.canDeleteModules(moduleEditors)) return false;

      var modulesToDelete = ContainerUtil.mapNotNull(moduleEditors, ModuleEditor::getModule);
      for (var module : modulesToDelete) {
        var removed = modulesConfigurator.getFacetsConfigurator().removeAllFacets(module);
        myProjectStructureConfigurable.getFacetStructureConfigurable().removeFacetNodes(removed);
      }
      modulesConfigurator.deleteModules(moduleEditors);
      for (var module : modulesToDelete) {
        myContext.getDaemonAnalyzer().removeElement(new ModuleProjectStructureElement(myContext, module));
        for (var extension : ModuleStructureExtension.EP_NAME.getExtensionList()) {
          extension.moduleRemoved(module);
        }
      }
      return true;
    }
  }

  private Module @Nullable [] getModuleContexts() {
    var paths = myTree.getSelectionPaths();
    var modules = new LinkedHashSet<Module>();
    if (paths != null) {
      for (var path : paths) {
        var node = (MyNode)path.getLastPathComponent();
        var configurable = node.getConfigurable();
        LOG.assertTrue(configurable != null, "already disposed");
        var o = configurable.getEditableObject();
        if (o instanceof Module module1) {
          modules.add(module1);
        }
        else if (node instanceof ModuleGroupNode groupNode && groupNode.getModuleGroup() != null) {
          TreeUtil.treeNodeTraverser(node).forEach(descendant -> {
            if (descendant instanceof MyNode myNode) {
              var object = myNode.getConfigurable().getEditableObject();
              if (object instanceof Module module) {
                modules.add(module);
              }
            }
          });
        }
      }
    }
    return !modules.isEmpty() ? modules.toArray(Module.EMPTY_ARRAY) : null;
  }

  private final class HideGroupsAction extends ToggleAction implements DumbAware {
    private HideGroupsAction() {
      super("", "", AllIcons.ObjectBrowser.CompactEmptyPackages);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      var presentation = e.getPresentation();
      var text = JavaUiBundle.message(myHideModuleGroups ? "project.roots.plain.mode.action.text.enabled" : "project.roots.plain.mode.action.text.disabled");
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
    var selection = (DefaultMutableTreeNode)null;
    var selectionPath = myTree.getSelectionPath();
    if (selectionPath != null) {
      selection = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
    }
    createGroupingHelper(Predicates.alwaysTrue()).moveAllModuleNodesToProperGroups(myRoot, getTreeModel());
    if (selection != null) {
      TreeUtil.selectInTree(selection, true, myTree);
    }
  }

  @Override
  protected AbstractAddGroup createAddAction(boolean fromPopup) {
    return new AbstractAddGroup(JavaUiBundle.message("add.new.header.text")) {
      @Override
      public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        var addModuleAction = new AddModuleAction(false, fromPopup);
        addModuleAction.getTemplatePresentation().setText(JavaUiBundle.message("action.text.new.module"));
        var result = new ArrayList<AnAction>();
        result.add(addModuleAction);

        var importModuleAction = new AddModuleAction(true, fromPopup);
        importModuleAction.getTemplatePresentation().setText(JavaUiBundle.message("action.text.import.module"));
        importModuleAction.getTemplatePresentation().setIcon(AllIcons.ToolbarDecorator.Import);
        result.add(importModuleAction);

        var actions = AddFacetToModuleAction.createAddFrameworkActions(myFacetEditorFacade, myProject);
        if (!actions.isEmpty()) {
          result.add(new Separator(JavaUiBundle.message("add.group.framework.separator")));
          result.addAll(actions);
        }

        var selectedNodeRetriever = (Supplier<MyNode>)() -> {
          var selectionPath = myTree.getSelectionPath();
          var lastPathComponent = selectionPath == null ? null : selectionPath.getLastPathComponent();
          if (lastPathComponent instanceof MyNode node) {
            return node;
          }
          return null;
        };

        var actionsFromExtensions = new ArrayList<AnAction>();
        for (var extension : ModuleStructureExtension.EP_NAME.getExtensionList()) {
          actionsFromExtensions.addAll(extension.createAddActions(selectedNodeRetriever, TREE_UPDATER, myProject, myRoot));
        }

        if (!actionsFromExtensions.isEmpty() && !result.isEmpty()) {
          result.add(new Separator());
        }
        result.addAll(actionsFromExtensions);
        return result.toArray(EMPTY_ARRAY);
      }
    };
  }

  @Override
  protected List<RemoveConfigurableHandler<?>> getRemoveHandlers() {
    return myRemoveHandlers;
  }

  @Override
  protected @NotNull String getEmptySelectionString() {
    return JavaUiBundle.message("empty.module.selection.string");
  }

  @SuppressWarnings("SplitModeApiUsage")
  private final class MyCopyAction extends AnAction implements DumbAware {
    private MyCopyAction() {
      super(CommonBundle.message("button.copy"), CommonBundle.message("button.copy"), COPY_ICON);
    }

    @Override
    @SuppressWarnings("ExtractMethodRecommender")
    public void actionPerformed(@NotNull AnActionEvent e) {
      var namedConfigurable = getSelectedConfigurable();
      if (!(namedConfigurable instanceof ModuleConfigurable moduleConfigurable)) {
        copyByExtension(namedConfigurable);
        return;
      }

      try {
        var moduleEditor = moduleConfigurable.getModuleEditor();
        var modulePresentation = IdeCoreBundle.message("project.new.wizard.module.identification");
        var component = new NamePathComponent(
          JavaUiBundle.message("label.module.name"),
          JavaUiBundle.message("label.component.file.location", StringUtil.capitalize(modulePresentation)),
          JavaUiBundle.message("title.select.project.file.directory", modulePresentation),
          JavaUiBundle.message("description.select.project.file.directory", StringUtil.capitalize(modulePresentation)),
          true, false
        );
        var originalModule = moduleEditor.getModule();
        if (originalModule != null) {
          component.setPath(originalModule.getModuleNioFile().getParent().toString());
        }

        var dialogBuilder = new DialogBuilder(myTree);
        dialogBuilder.setTitle(JavaUiBundle.message("copy.module.dialog.title"));
        dialogBuilder.setCenterPanel(component);
        dialogBuilder.setPreferredFocusComponent(component.getNameComponent());
        dialogBuilder.setOkOperation(() -> {
          var name = component.getNameValue();
          if (name.isEmpty()) {
            Messages.showErrorDialog(JavaUiBundle.message("enter.module.copy.name.error.message"), CommonBundle.getErrorTitle());
            return;
          }
          if (getModule(name) != null) {
            Messages.showErrorDialog(JavaUiBundle.message("module.0.already.exists.error.message", name), CommonBundle.getErrorTitle());
            return;
          }

          if (component.getPath().isEmpty()) {
            Messages.showErrorDialog(JavaUiBundle.message("prompt.enter.project.file.location", modulePresentation), CommonBundle.getErrorTitle());
            return;
          }
          if (!ProjectWizardUtil.createDirectoryIfNotExists(JavaUiBundle.message("directory.project.file.directory", modulePresentation), component.getPath(), true)) {
            Messages.showErrorDialog(JavaUiBundle.message("path.0.is.invalid.error.message", component.getPath()), CommonBundle.getErrorTitle());
            return;
          }
          dialogBuilder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
        });
        if (dialogBuilder.show() != DialogWrapper.OK_EXIT_CODE) {
          return;
        }

        var rootModel = moduleEditor.getModifiableRootModel();
        var path = Paths.get(component.getPath());
        ModuleBuilder builder = new CopiedModuleBuilder(rootModel, path, myProject);
        builder.setName(component.getNameValue());
        builder.setModuleFilePath(path.resolve(builder.getName() + ModuleFileType.DOT_DEFAULT_EXTENSION).toString());
        var module = myContext.myModulesConfigurator.addModule(builder);
        if (module != null) {
          addModuleNode(module);
        }
      }
      catch (Exception ex) {
        LOG.error(ex);
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      var selectionPaths = myTree.getSelectionPaths();
      if (selectionPaths == null || selectionPaths.length != 1) {
        e.getPresentation().setEnabled(false);
      }
      else {
        var selectedConfigurable = getSelectedConfigurable();
        e.getPresentation().setEnabled(selectedConfigurable instanceof ModuleConfigurable || canBeCopiedByExtension(selectedConfigurable));
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  @ApiStatus.Internal
  public static final class CopiedModuleBuilder extends ModuleBuilder {
    private final @NotNull ModifiableRootModel myRootModel;
    private final @NotNull Path myComponentPath;
    private final @NotNull Project myProject;

    @VisibleForTesting
    public CopiedModuleBuilder(@NotNull ModifiableRootModel rootModel, @NotNull Path componentPath, @NotNull Project project) {
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

      for (var entry : myRootModel.getOrderEntries()) {
        if (entry instanceof JdkOrderEntry) continue;
        if (entry instanceof ModuleSourceOrderEntry) continue;
        if (entry instanceof ClonableOrderEntry orderEntry) {
          modifiableRootModel.addOrderEntry(orderEntry.cloneEntry(
            modifiableRootModel, (ProjectRootManagerImpl)ProjectRootManager.getInstance(myProject), VirtualFilePointerManager.getInstance()
          ));
        }
      }

      var content = VirtualFileManager.getInstance().findFileByNioPath(myComponentPath);
      if (content == null) {
        try {
          Files.createFile(NioFiles.createParentDirectories(myComponentPath));
        }
        catch (IOException e) {
          throw new UncheckedIOException(e);
        }
        content = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(myComponentPath);
      }
      if (content != null) {
        modifiableRootModel.addContentEntry(content);
      }
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
    public void actionPerformed(@NotNull AnActionEvent e) {
      addModule(myImport, myDetectModuleBase);
    }
  }

  private static final class MergingComparator<T> implements Comparator<T> {
    private final List<? extends Comparator<T>> myDelegates;

    private MergingComparator(List<? extends Comparator<T>> delegates) {
      myDelegates = delegates;
    }

    @Override
    public int compare(T o1, T o2) {
      for (var delegate : myDelegates) {
        var value = delegate.compare(o1, o2);
        if (value != 0) return value;
      }
      return 0;
    }
  }
}
