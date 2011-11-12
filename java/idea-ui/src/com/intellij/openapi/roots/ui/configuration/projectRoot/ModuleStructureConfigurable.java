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

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.CommonBundle;
import com.intellij.facet.Facet;
import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.facet.impl.ui.actions.AddFacetToModuleAction;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.ModuleGroupUtil;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.NamePathComponent;
import com.intellij.ide.util.projectWizard.ProjectWizardUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ClonableOrderEntry;
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl;
import com.intellij.openapi.roots.impl.RootModelImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.LibraryProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ModuleProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureDaemonAnalyzer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.navigation.Place;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * User: anna
 * Date: 02-Jun-2006
 */
public class ModuleStructureConfigurable extends BaseStructureConfigurable implements Place.Navigator {
  private static final Icon COMPACT_EMPTY_MIDDLE_PACKAGES_ICON = IconLoader.getIcon("/objectBrowser/compactEmptyPackages.png");
  private static final Icon ICON = IconLoader.getIcon("/modules/modules.png");
  private static final Comparator<MyNode> NODE_COMPARATOR = new Comparator<MyNode>() {
    public int compare(final MyNode o1, final MyNode o2) {
      final NamedConfigurable configurable1 = o1.getConfigurable();
      final NamedConfigurable configurable2 = o2.getConfigurable();
      if (configurable1.getClass() == configurable2.getClass()) {
        return o1.getDisplayName().compareToIgnoreCase(o2.getDisplayName());
      }
      final Object editableObject1 = configurable1.getEditableObject();
      final Object editableObject2 = configurable2.getEditableObject();

      if (editableObject2 instanceof Module && editableObject1 instanceof ModuleGroup) return -1;
      if (editableObject1 instanceof Module && editableObject2 instanceof ModuleGroup) return 1;

      if (editableObject2 instanceof Module && editableObject1 instanceof String) return 1;
      if (editableObject1 instanceof Module && editableObject2 instanceof String) return -1;

      if (editableObject2 instanceof ModuleGroup && editableObject1 instanceof String) return 1;
      if (editableObject1 instanceof ModuleGroup && editableObject2 instanceof String) return -1;

      return 0;
    }
  };

  private boolean myPlainMode;

  private final ModuleManager myModuleManager;

  private final FacetEditorFacadeImpl myFacetEditorFacade = new FacetEditorFacadeImpl(this, TREE_UPDATER);


  public ModuleStructureConfigurable(Project project, ModuleManager manager) {
    super(project);
    myModuleManager = manager;
  }

  @Override
  protected String getComponentStateKey() {
    return "ModuleStructureConfigurable.UI";
  }

  protected void initTree() {
    super.initTree();
    myTree.setRootVisible(false);
  }

  protected ArrayList<AnAction> getAdditionalActions() {
    final ArrayList<AnAction> result = new ArrayList<AnAction>();
    result.add(ActionManager.getInstance().getAction(IdeActions.GROUP_MOVE_MODULE_TO_GROUP));
    return result;
  }

  @Override
  public void addNode(MyNode nodeToAdd, MyNode parent) {
    super.addNode(nodeToAdd, parent);
  }

  @NotNull
  protected ArrayList<AnAction> createActions(final boolean fromPopup) {
    final ArrayList<AnAction> result = super.createActions(fromPopup);
    result.add(Separator.getInstance());
    result.add(new MyGroupAction());
    addCollapseExpandActions(result);
    return result;
  }

  @NotNull
  protected List<? extends AnAction> createCopyActions(boolean fromPopup) {
    return Collections.singletonList(new MyCopyAction());
  }

  protected void loadTree() {
    createProjectNodes();

    ((DefaultTreeModel)myTree.getModel()).reload();

    myUiDisposed = false;
  }

  @NotNull
  @Override
  protected Collection<? extends ProjectStructureElement> getProjectStructureElements() {
    final List<ProjectStructureElement> result = new ArrayList<ProjectStructureElement>();
    for (Module module : myModuleManager.getModules()) {
      result.add(new ModuleProjectStructureElement(myContext, module));
    }
    return result;
  }

  protected void updateSelection(@Nullable final NamedConfigurable configurable) {
    FacetStructureConfigurable.getInstance(myProject).disposeMultipleSettingsEditor();
    ApplicationManager.getApplication().assertIsDispatchThread();
    super.updateSelection(configurable);
    if (configurable != null) {
      updateModuleEditorSelection(configurable);
    }
  }


  protected boolean isAutoScrollEnabled() {
    return myAutoScrollEnabled;
  }

  protected boolean updateMultiSelection(final List<NamedConfigurable> selectedConfigurables) {
    return FacetStructureConfigurable.getInstance(myProject).updateMultiSelection(selectedConfigurables, getDetailsComponent());
  }

  private void updateModuleEditorSelection(final NamedConfigurable configurable) {
    if (configurable instanceof ModuleConfigurable){
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
    final Map<ModuleGroup, MyNode> moduleGroup2NodeMap = new HashMap<ModuleGroup, MyNode>();
    final Module[] modules = myModuleManager.getModules();
    for (final Module module : modules) {
      ModuleConfigurable configurable = new ModuleConfigurable(myContext.myModulesConfigurator, module, TREE_UPDATER);
      final MyNode moduleNode = new MyNode(configurable);
      boolean nodesAdded = myFacetEditorFacade.addFacetsNodes(module, moduleNode);
      nodesAdded |= addNodesFromExtensions(module, moduleNode);
      if (nodesAdded) {
        myTree.setShowsRootHandles(true);
      }
      final String[] groupPath = myPlainMode ? null : myContext.myModulesConfigurator.getModuleModel().getModuleGroupPath(module);
      if (groupPath == null || groupPath.length == 0){
        addNode(moduleNode, myRoot);
      } else {
        final MyNode moduleGroupNode = ModuleGroupUtil
          .buildModuleGroupPath(new ModuleGroup(groupPath), myRoot, moduleGroup2NodeMap,
                                new Consumer<ModuleGroupUtil.ParentChildRelation<MyNode>>() {
                                  public void consume(final ModuleGroupUtil.ParentChildRelation<MyNode> parentChildRelation) {
                                    addNode(parentChildRelation.getChild(), parentChildRelation.getParent());
                                  }
                                },
                                new Function<ModuleGroup, MyNode>() {
                                  public MyNode fun(final ModuleGroup moduleGroup) {
                                    final NamedConfigurable moduleGroupConfigurable =
                                      createModuleGroupConfigurable(moduleGroup);
                                    return new MyNode(moduleGroupConfigurable, true);
                                  }
                                });
        addNode(moduleNode, moduleGroupNode);
      }
    }
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

  private void addRootNodesFromExtensions(final MyNode root, final Project project) {
    for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
      extension.addRootNodes(root, project, TREE_UPDATER);
    }
  }

  private boolean addNodesFromExtensions(final Module module, final MyNode moduleNode) {
    boolean nodesAdded = false;
    for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
      nodesAdded |= extension.addModuleNodeChildren(module, moduleNode, TREE_UPDATER);
    }
    return nodesAdded;
  }

  public boolean updateProjectTree(final Module[] modules, final ModuleGroup group) {
    if (myRoot.getChildCount() == 0) return false; //isn't visible
    final MyNode [] nodes = new MyNode[modules.length];
    int i = 0;
    for (Module module : modules) {
      MyNode node = findModuleNode(module);
      LOG.assertTrue(node != null, "Module " + module.getName() + " is not in project.");
      node.removeFromParent();
      nodes[i ++] = node;
    }
    for (final MyNode moduleNode : nodes) {
      final String[] groupPath = myPlainMode
                                 ? null
                                 : group != null ? group.getGroupPath() : null;
      if (groupPath == null || groupPath.length == 0){
        addNode(moduleNode, myRoot);
      } else {
        final MyNode moduleGroupNode = ModuleGroupUtil
          .updateModuleGroupPath(new ModuleGroup(groupPath), myRoot, new Function<ModuleGroup, MyNode>() {
            @Nullable
            public MyNode fun(final ModuleGroup group) {
              return findNodeByObject(myRoot, group);
            }
          }, new Consumer<ModuleGroupUtil.ParentChildRelation<MyNode>>() {
            public void consume(final ModuleGroupUtil.ParentChildRelation<MyNode> parentChildRelation) {
              addNode(parentChildRelation.getChild(), parentChildRelation.getParent());
            }
          }, new Function<ModuleGroup, MyNode>() {
            public MyNode fun(final ModuleGroup moduleGroup) {
              final NamedConfigurable moduleGroupConfigurable = createModuleGroupConfigurable(moduleGroup);
              return new MyNode(moduleGroupConfigurable, true);
            }
          });
        addNode(moduleNode, moduleGroupNode);
      }
      Module module = (Module)moduleNode.getConfigurable().getEditableObject();
      myFacetEditorFacade.addFacetsNodes(module, moduleNode);
      addNodesFromExtensions(module, moduleNode);
    }
    TreeUtil.sort(myRoot, getNodeComparator());
    ((DefaultTreeModel)myTree.getModel()).reload(myRoot);
    return true;
  }

  @Override
  protected Comparator<MyNode> getNodeComparator() {
    List<Comparator<MyNode>> comparators = ContainerUtil
      .mapNotNull(ModuleStructureExtension.EP_NAME.getExtensions(), new Function<ModuleStructureExtension, Comparator<MyNode>>() {
        public Comparator<MyNode> fun(final ModuleStructureExtension moduleStructureExtension) {
          return moduleStructureExtension.getNodeComparator();
        }
      });
    comparators.add(NODE_COMPARATOR);
    return new MergingComparator<MyNode>(comparators);
  }

  public void init(final StructureConfigurableContext context) {
    super.init(context);

    addItemsChangeListener(new ItemsChangeListener() {
      public void itemChanged(@Nullable Object deletedItem) {
        if (deletedItem instanceof Library) {
          final Library library = (Library)deletedItem;
          final MyNode node = findNodeByObject(myRoot, library);
          if (node != null) {
            final TreeNode parent = node.getParent();
            node.removeFromParent();
            ((DefaultTreeModel)myTree.getModel()).reload(parent);
          }
          myContext.getDaemonAnalyzer().removeElement(new LibraryProjectStructureElement(myContext, library));
        }
      }

      public void itemsExternallyChanged() {
        //do nothing
      }
    });
  }

  public void reset() {
    super.reset();
    for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
      extension.reset(myProject);
    }
  }

  public void apply() throws ConfigurationException {
    final Set<MyNode> roots = new HashSet<MyNode>();
    roots.add(myRoot);
    checkApply(roots, ProjectBundle.message("rename.message.prefix.module"), ProjectBundle.message("rename.module.title"));

    // let's apply extensions first, since they can write to/commit modifiable models
    for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
      if (extension.isModified()) {
        extension.apply();
      }
    }

    if (myContext.myModulesConfigurator.isModified()) {
      myContext.myModulesConfigurator.apply();
    }
  }

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

  public void disposeUIResources() {
    super.disposeUIResources();
    myFacetEditorFacade.clearMaps(true);
    myContext.myModulesConfigurator.disposeUIResources();
    ModuleStructureConfigurable.super.disposeUIResources();

    for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
      extension.disposeUIResources();
    }
  }

  public void dispose() {}


  public JComponent createComponent() {
    return new MyDataProviderWrapper(super.createComponent());
  }

  protected void processRemovedItems() {
    // do nothing
  }

  protected boolean wasObjectStored(Object editableObject) {
    return false;
  }

  public String getDisplayName() {
    return ProjectBundle.message("project.roots.display.name");
  }

  public Icon getIcon() {
    return ICON;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    final String topic = super.getHelpTopic();
    if (topic != null) {
      return topic;
    }
    return "reference.settingsdialog.project.structure.module";
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
    } else {
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
    Component parent = WindowManager.getInstance().suggestParentWindow(module.getProject());

    final ModuleEditor moduleEditor = myContext.myModulesConfigurator.getModuleEditor(module);
    LOG.assertTrue(moduleEditor != null, "Current module editor was not initialized");
    final ModifiableRootModel modelProxy = moduleEditor.getModifiableRootModelProxy();
    final OrderEntry[] entries = modelProxy.getOrderEntries();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry && Comparing.strEqual(entry.getPresentableName(), library.getName())) {
        if (Messages.showYesNoDialog(parent,
                                     ProjectBundle.message("project.roots.replace.library.entry.message", entry.getPresentableName()),
                                     ProjectBundle.message("project.roots.replace.library.entry.title"),
                                     Messages.getInformationIcon()) == DialogWrapper.OK_EXIT_CODE) {
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

  private void addModule() {
    final List<Module> modules = myContext.myModulesConfigurator.addModule(myTree);
    if (modules != null) {
      for (Module module : modules) {
        addModuleNode(module);
      }
    }
  }

  private void addModuleNode(final Module module) {
    final MyNode node = new MyNode(new ModuleConfigurable(myContext.myModulesConfigurator, module, TREE_UPDATER));
    final TreePath selectionPath = myTree.getSelectionPath();
    MyNode parent = null;
    if (selectionPath != null) {
      MyNode selected = (MyNode)selectionPath.getLastPathComponent();
      final Object o = selected.getConfigurable().getEditableObject();
      if (o instanceof ModuleGroup) {
        myContext.myModulesConfigurator.getModuleModel().setModuleGroupPath(module, ((ModuleGroup)o).getGroupPath());
        parent = selected;
      } else if (o instanceof Module) { //create near selected
        final ModifiableModuleModel modifiableModuleModel = myContext.myModulesConfigurator.getModuleModel();
        final String[] groupPath = modifiableModuleModel.getModuleGroupPath((Module)o);
        if (groupPath != null) {
          modifiableModuleModel.setModuleGroupPath(module, groupPath);
          parent = findNodeByObject(myRoot, new ModuleGroup(groupPath));
        }
      }
    }
    if (parent == null) parent = myRoot;
    addNode(node, parent);
    myFacetEditorFacade.addFacetsNodes(module, node);
    addNodesFromExtensions(module, node);
    ((DefaultTreeModel)myTree.getModel()).reload(parent);
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

  @NotNull
  @NonNls
  public String getId() {
    return "project.structure";
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }


  @Nullable
  public Module getModule(final String moduleName) {
    if (moduleName == null) return null;
    return (myContext != null && myContext.myModulesConfigurator != null) ? myContext.myModulesConfigurator.getModule(moduleName) : myModuleManager.findModuleByName(moduleName);
  }

  public StructureConfigurableContext getContext() {
    return myContext;
  }

  private static TextConfigurable<ModuleGroup> createModuleGroupConfigurable(final ModuleGroup moduleGroup) {
    return new TextConfigurable<ModuleGroup>(moduleGroup, moduleGroup.toString(),
                                             ProjectBundle.message("module.group.banner.text", moduleGroup.toString()),
                                             ProjectBundle.message("project.roots.module.groups.text"),
                                             PlatformIcons.OPENED_MODULE_GROUP_ICON, PlatformIcons.CLOSED_MODULE_GROUP_ICON);
  }

  protected boolean canBeRemoved(final Object[] editableObjects) {
    if (super.canBeRemoved(editableObjects)) {
      return true;
    }
    for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
      if (extension.canBeRemoved(editableObjects)) {
        return true;
      }
    }

    return false;
  }

  protected boolean removeObject(final Object editableObject) {
    for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
      if (extension.removeObject(editableObject)) {
        return true;
      }
    }
    return super.removeObject(editableObject);
  }

  private boolean canBeCopiedByExtension(final NamedConfigurable configurable) {
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
  
  private class MyDataProviderWrapper extends JPanel implements DataProvider {
    public MyDataProviderWrapper(final JComponent component) {
      super(new BorderLayout());
      add(component, BorderLayout.CENTER);
    }

    @Nullable
    public Object getData(@NonNls String dataId) {
      if (DataKeys.MODULE_CONTEXT_ARRAY.is(dataId)){
        final TreePath[] paths = myTree.getSelectionPaths();
        if (paths != null) {
          ArrayList<Module> modules = new ArrayList<Module>();
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
      if (DataKeys.MODULE_CONTEXT.is(dataId)){
        return getSelectedModule();
      }
      if (LangDataKeys.MODIFIABLE_MODULE_MODEL.is(dataId)){
        return myContext.myModulesConfigurator.getModuleModel();
      }

      return null;
    }
  }


  private class MyGroupAction extends ToggleAction implements DumbAware {

    public MyGroupAction() {
      super("", "", COMPACT_EMPTY_MIDDLE_PACKAGES_ICON);
    }

    public void update(final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      String text = ProjectBundle.message("project.roots.plain.mode.action.text.disabled");
      if (myPlainMode){
        text = ProjectBundle.message("project.roots.plain.mode.action.text.enabled");
      }
      presentation.setText(text);
      presentation.setDescription(text);

      if (myContext.myModulesConfigurator != null) {
        presentation.setVisible(myContext.myModulesConfigurator.getModuleModel().hasModuleGroups());
      }
    }

    public boolean isSelected(AnActionEvent e) {
      return myPlainMode;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      myPlainMode = state;
      DefaultMutableTreeNode selection = null;
      final TreePath selectionPath = myTree.getSelectionPath();
      if (selectionPath != null){
        selection = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
      }
      final ModifiableModuleModel model = myContext.myModulesConfigurator.getModuleModel();
      final Module[] modules = model.getModules();
      for (Module module : modules) {
        final String[] groupPath = model.getModuleGroupPath(module);
        updateProjectTree(new Module[]{module}, groupPath != null ? new ModuleGroup(groupPath) : null);
      }
      if (state) {
        removeModuleGroups();
      }
      if (selection != null){
        TreeUtil.selectInTree(selection, true, myTree);
      }
    }

    private void removeModuleGroups() {
      for(int i = myRoot.getChildCount() - 1; i >=0; i--){
        final MyNode node = (MyNode)myRoot.getChildAt(i);
        if (node.getConfigurable().getEditableObject() instanceof ModuleGroup){
          node.removeFromParent();
        }
      }
      ((DefaultTreeModel)myTree.getModel()).reload(myRoot);
    }
  }

  protected AbstractAddGroup createAddAction() {
    return new AbstractAddGroup(ProjectBundle.message("add.new.header.text")) {
      @NotNull
      public AnAction[] getChildren(@Nullable final AnActionEvent e) {
        AnAction module = new AddModuleAction();

        ArrayList<AnAction> result = new ArrayList<AnAction>();
        result.add(module);

        final Collection<AnAction> actions = AddFacetToModuleAction.createAddFrameworkActions(myFacetEditorFacade, myProject);
        if (!actions.isEmpty()) {
          result.add(new Separator(ProjectBundle.message("add.group.framework.separator")));
          result.addAll(actions);
        }

        final NullableComputable<MyNode> selectedNodeRetriever = new NullableComputable<MyNode>() {
          public MyNode compute() {
            final TreePath selectionPath = myTree.getSelectionPath();
            final Object lastPathComponent = selectionPath == null ? null : selectionPath.getLastPathComponent();
            if (lastPathComponent instanceof MyNode) {
              return (MyNode)lastPathComponent;
            }
            return null;
          }
        };
        for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
          result.addAll(extension.createAddActions(selectedNodeRetriever, TREE_UPDATER, myProject, myRoot));
        }

        return result.toArray(new AnAction[result.size()]);
      }
    };
  }

  protected List<Facet> removeFacet(final Facet facet) {
    List<Facet> removed = super.removeFacet(facet);
    FacetStructureConfigurable.getInstance(myProject).removeFacetNodes(removed);
    return removed;
  }

  protected boolean removeModule(final Module module) {
    ModulesConfigurator modulesConfigurator = myContext.myModulesConfigurator;
    if (!modulesConfigurator.deleteModule(module)) {
      //wait for confirmation
      return false;
    }
    List<Facet> removed = modulesConfigurator.getFacetsConfigurator().removeAllFacets(module);
    FacetStructureConfigurable.getInstance(myProject).removeFacetNodes(removed);
    myContext.getDaemonAnalyzer().removeElement(new ModuleProjectStructureElement(myContext, module));

    for (final ModuleStructureExtension extension : ModuleStructureExtension.EP_NAME.getExtensions()) {
      extension.moduleRemoved(module);
    }
    return true;
  }

  @Nullable
  protected String getEmptySelectionString() {
    return ProjectBundle.message("empty.module.selection.string");
  }

  private class MyCopyAction extends AnAction implements DumbAware {
    private MyCopyAction() {
      super(CommonBundle.message("button.copy"), CommonBundle.message("button.copy"), COPY_ICON);
    }

    public void actionPerformed(final AnActionEvent e) {
      final NamedConfigurable namedConfigurable = getSelectedConfugurable();
      if (namedConfigurable instanceof ModuleConfigurable) {
        try {
          final ModuleEditor moduleEditor = ((ModuleConfigurable)namedConfigurable).getModuleEditor();
          final String modulePresentation = IdeBundle.message("project.new.wizard.module.identification");
          final NamePathComponent component = new NamePathComponent(IdeBundle.message("label.module.name"), IdeBundle.message("label.component.file.location", StringUtil.capitalize(modulePresentation)), IdeBundle.message("title.select.project.file.directory", modulePresentation),
                                                                    IdeBundle.message("description.select.project.file.directory", StringUtil.capitalize(modulePresentation)), true,
                                                                    false);
          final Module originalModule = moduleEditor.getModule();
          if (originalModule != null) {
            component.setPath(PathUtil.getParentPath(originalModule.getModuleFilePath()));
          }

          final DialogBuilder dialogBuilder = new DialogBuilder(myTree);
          dialogBuilder.setTitle(ProjectBundle.message("copy.module.dialog.title"));
          dialogBuilder.setCenterPanel(component);
          dialogBuilder.setPreferedFocusComponent(component.getNameComponent());
          dialogBuilder.setOkOperation(new Runnable() {
            @Override
            public void run() {
              final String name = component.getNameValue();
              if (name.length() == 0) {
                Messages.showErrorDialog(ProjectBundle.message("enter.module.copy.name.error.message"), CommonBundle.message("title.error"));
                return;
              }
              if (getModule(name) != null) {
                Messages.showErrorDialog(ProjectBundle.message("module.0.already.exists.error.message", name), CommonBundle.message("title.error"));
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
                Messages.showErrorDialog(ProjectBundle.message("path.0.is.invalid.error.message", component.getPath()), CommonBundle.message("title.error"));
                 return;
              }
              dialogBuilder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
            }
          });
          if (dialogBuilder.show() != DialogWrapper.OK_EXIT_CODE) return;

          final ModifiableRootModel rootModel = moduleEditor.getModifiableRootModel();
          final String path = component.getPath();
          final ModuleBuilder builder = new ModuleBuilder() {
            public void setupRootModel(final ModifiableRootModel modifiableRootModel) throws ConfigurationException {
              if (rootModel.isSdkInherited()) {
                modifiableRootModel.inheritSdk();
              }
              else {
                modifiableRootModel.setSdk(rootModel.getSdk());
              }

              modifiableRootModel.getModuleExtension(CompilerModuleExtension.class).inheritCompilerOutputPath(true);

              modifiableRootModel.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(LanguageLevelModuleExtension.getInstance(rootModel.getModule()).getLanguageLevel());

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

    public void update(final AnActionEvent e) {
      TreePath[] selectionPaths = myTree.getSelectionPaths();
      if (selectionPaths == null || selectionPaths.length != 1) {
        e.getPresentation().setEnabled(false);
      } else {
        final NamedConfigurable selectedConfigurable = getSelectedConfugurable();
        e.getPresentation().setEnabled(selectedConfigurable instanceof ModuleConfigurable || canBeCopiedByExtension(selectedConfigurable));
      }
    }
  }

  private class AddModuleAction extends AnAction implements DumbAware {
    public AddModuleAction() {
      super(ProjectBundle.message("add.new.module.text.full"), null, IconLoader.getIcon("/actions/modul.png"));
    }

    public void actionPerformed(final AnActionEvent e) {
      addModule();
    }
  }

  private static class MergingComparator<T> implements Comparator<T> {
    private final List<Comparator<T>> myDelegates;

    public MergingComparator(final List<Comparator<T>> delegates) {
      myDelegates = delegates;
    }

    public int compare(final T o1, final T o2) {
      for (Comparator<T> delegate : myDelegates) {
        int value = delegate.compare(o1, o2);
        if (value != 0) return value;
      }
      return 0;
    }
  }

}
