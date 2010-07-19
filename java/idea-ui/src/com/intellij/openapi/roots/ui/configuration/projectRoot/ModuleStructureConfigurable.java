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
import com.intellij.facet.impl.ui.actions.AddFacetActionGroup;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.ModuleGroupUtil;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.NamePathComponent;
import com.intellij.ide.util.projectWizard.ProjectWizardUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.*;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.navigation.Place;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
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
@State(
  name = "ModuleStructureConfigurable.UI",
  storages = {
    @Storage(
      id ="other",
      file = "$WORKSPACE_FILE$"
    )}
)
public class ModuleStructureConfigurable extends BaseStructureConfigurable implements Place.Navigator {

  private static final Icon COMPACT_EMPTY_MIDDLE_PACKAGES_ICON = IconLoader.getIcon("/objectBrowser/compactEmptyPackages.png");
  private static final Icon ICON = IconLoader.getIcon("/modules/modules.png");

  private boolean myPlainMode;

  private final ModuleManager myModuleManager;

  private final FacetEditorFacadeImpl myFacetEditorFacade = new FacetEditorFacadeImpl(this, TREE_UPDATER);


  public ModuleStructureConfigurable(Project project, ModuleManager manager) {
    super(project);
    myModuleManager = manager;
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

  @NotNull
  protected ArrayList<AnAction> createActions(final boolean fromPopup) {
    final ArrayList<AnAction> result = super.createActions(fromPopup);
    result.add(Separator.getInstance());
    result.add(new MyGroupAction());
    addCollapseExpandActions(result);
    return result;
  }

  protected AnAction createCopyAction() {
    return new MyCopyAction();
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
    final String selectedTab = ModuleEditor.getSelectedTab();
    updateSelection(configurable, selectedTab);
  }


  protected boolean isAutoScrollEnabled() {
    return myAutoScrollEnabled;
  }

  protected boolean updateMultiSelection(final List<NamedConfigurable> selectedConfigurables) {
    return FacetStructureConfigurable.getInstance(myProject).updateMultiSelection(selectedConfigurables, getDetailsComponent());
  }

  private void updateSelection(final NamedConfigurable configurable, final String selectedTab) {
    super.updateSelection(configurable);
    if (configurable != null) {
      updateTabSelection(configurable, selectedTab);
    }
  }

  private void updateTabSelection(final NamedConfigurable configurable, final String selectedTab) {
    if (configurable instanceof ModuleConfigurable){
      final ModuleConfigurable moduleConfigurable = (ModuleConfigurable)configurable;
      final ModuleEditor editor = moduleConfigurable.getModuleEditor();
      if (editor != null) { //already deleted
        editor.init(selectedTab, myHistory);
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
      final boolean facetsExist = myFacetEditorFacade.addFacetsNodes(module, moduleNode);
      if (facetsExist) {
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
                                    final NamedConfigurable moduleGroupConfigurable = new ModuleGroupConfigurable(moduleGroup);
                                    return new MyNode(moduleGroupConfigurable, true);
                                  }
                                });
        addNode(moduleNode, moduleGroupNode);
      }
    }
    if (myProject.isDefault()) {  //do not add modules node in case of template project
      myRoot.removeAllChildren();
    }

    //final LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
    //final LibrariesModifiableModel projectLibrariesProvider = new LibrariesModifiableModel(table);
    //myLevel2Providers.put(LibraryTablesRegistrar.PROJECT_LEVEL, projectLibrariesProvider);
    //
    //myProjectNode.add(myLevel2Nodes.get(LibraryTablesRegistrar.PROJECT_LEVEL));
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
              final NamedConfigurable moduleGroupConfigurable = new ModuleGroupConfigurable(moduleGroup);
              return new MyNode(moduleGroupConfigurable, true);
            }
          });
        addNode(moduleNode, moduleGroupNode);
      }
      myFacetEditorFacade.addFacetsNodes((Module)moduleNode.getConfigurable().getEditableObject(), moduleNode);
    }
    ((DefaultTreeModel)myTree.getModel()).reload(myRoot);
    return true;
  }

  protected void addNode(MyNode nodeToAdd, MyNode parent) {
    parent.add(nodeToAdd);
    TreeUtil.sort(parent, new Comparator() {
      public int compare(final Object o1, final Object o2) {
        final MyNode node1 = (MyNode)o1;
        final MyNode node2 = (MyNode)o2;
        final Object editableObject1 = node1.getConfigurable().getEditableObject();
        final Object editableObject2 = node2.getConfigurable().getEditableObject();
        if (editableObject1.getClass() == editableObject2.getClass()) {
          return node1.getDisplayName().compareToIgnoreCase(node2.getDisplayName());
        }

        if (editableObject2 instanceof Module && editableObject1 instanceof ModuleGroup) return -1;
        if (editableObject1 instanceof Module && editableObject2 instanceof ModuleGroup) return 1;

        if (editableObject2 instanceof Module && editableObject1 instanceof String) return 1;
        if (editableObject1 instanceof Module && editableObject2 instanceof String) return -1;

        if (editableObject2 instanceof ModuleGroup && editableObject1 instanceof String) return 1;
        if (editableObject1 instanceof ModuleGroup && editableObject2 instanceof String) return -1;

        return 0;
      }
    });
    ((DefaultTreeModel)myTree.getModel()).reload(parent);
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
          // TODO: myContext.invalidateModules(myContext.myLibraryDependencyCache.get(library.getName()));
        }
      }

      public void itemsExternallyChanged() {
        //do nothing
      }
    });
  }

  public void reset() {
    super.reset();
  }


  public void apply() throws ConfigurationException {
    final Set<MyNode> roots = new HashSet<MyNode>();
    roots.add(myRoot);
    checkApply(roots, ProjectBundle.message("rename.message.prefix.module"), ProjectBundle.message("rename.module.title"));

    if (myContext.myModulesConfigurator.isModified()) myContext.myModulesConfigurator.apply();
  }

  public boolean isModified() {
    return myContext.myModulesConfigurator.isModified();
  }

  public void disposeUIResources() {
    super.disposeUIResources();
    myFacetEditorFacade.clearMaps(true);
    myContext.myModulesConfigurator.disposeUIResources();
    ModuleStructureConfigurable.super.disposeUIResources();
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
    return ShowSettingsUtil.getInstance().findProjectConfigurable(project, ModuleStructureConfigurable.class);
  }

  public void setStartModuleWizard(final boolean show) {
    myContext.myModulesConfigurator.getModulesConfigurable().setStartModuleWizardOnShow(show);
  }

  public Project getProject() {
    return myProject;
  }

  public void selectOrderEntry(@NotNull final Module module, @Nullable final OrderEntry orderEntry) {
    ProjectStructureConfigurable.getInstance(myProject).select(module.getName(), null, true).doWhenDone(new Runnable() {
      public void run() {
        final MyNode node = findModuleNode(module);
        if (node != null) {
          ModuleConfigurable moduleConfigurable = (ModuleConfigurable)node.getConfigurable();
          ModuleEditor moduleEditor = moduleConfigurable.getModuleEditor();
          moduleEditor.setSelectedTabName(ClasspathEditor.NAME);
          if (orderEntry != null) {
            ModuleConfigurationEditor editor = moduleEditor.getEditor(ClasspathEditor.NAME);
            if (editor instanceof ClasspathEditor) {
              ((ClasspathEditor)editor).selectOrderEntry(orderEntry);
            }
          }
        }
      }
    });
  }

  public Module[] getModules() {
    if (myContext.myModulesConfigurator != null) {
      final ModifiableModuleModel model = myContext.myModulesConfigurator.getModuleModel();
      return model.getModules();
    } else {
      return myModuleManager.getModules();
    }
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
    /* TODO
    Set<String> modules = myContext.myLibraryDependencyCache.get(library.getName());
    if (modules == null) {
      modules = new HashSet<String>();
      myContext.myLibraryDependencyCache.put(library.getName(), modules);
    }
    modules.add(module.getName());
    */
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
    ((DefaultTreeModel)myTree.getModel()).reload(parent);
    selectNodeInTree(node);
    final ProjectStructureDaemonAnalyzer daemonAnalyzer = myContext.getDaemonAnalyzer();
    daemonAnalyzer.queueUpdate(new ModuleProjectStructureElement(myContext, module));
    daemonAnalyzer.clearAllProblems(); //missing modules added
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
    final AddFacetActionGroup addFacetGroup = new AddFacetActionGroup("", true, myFacetEditorFacade);

    return new AbstractAddGroup(ProjectBundle.message("add.new.header.text")) {
      @NotNull
      public AnAction[] getChildren(@Nullable final AnActionEvent e) {
        AnAction module = new AddModuleAction();

        ArrayList<AnAction> result = new ArrayList<AnAction>();
        result.add(module);

        final AnAction[] facets = addFacetGroup.getChildren(e);
        if (facets.length > 0) {
          result.add(new Separator(ProjectBundle.message("add.group.facet.separator")));
        }

        ContainerUtil.addAll(result, facets);

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
          final String modulePresentation = IdeBundle.message("project.new.wizard.module.identification");
          final NamePathComponent component = new NamePathComponent(IdeBundle.message("label.project.name"), IdeBundle.message(
            "label.component.file.location", StringUtil.capitalize(modulePresentation)), 'a', 'l', IdeBundle.message(
            "title.select.project.file.directory", modulePresentation), IdeBundle.message("description.select.project.file.directory",
                                                                                          StringUtil.capitalize(modulePresentation)));
          final DialogWrapper copyModuleDialog = new DialogWrapper(myTree, false) {
            {
              setTitle(ProjectBundle.message("copy.module.dialog.title"));
              init();
            }

            public JComponent getPreferredFocusedComponent() {
              return component.getNameComponent();
            }

            @Nullable
            protected JComponent createCenterPanel() {
              return component;
            }

            protected void doOKAction() {
              if (component.getNameValue().length() == 0) {
                Messages.showErrorDialog(ProjectBundle.message("enter.module.copy.name.error.message"), CommonBundle.message("title.error"));
                return;
              }

              if (component.getPath().length() == 0) {
                Messages.showErrorDialog(IdeBundle.message("prompt.enter.project.file.location", modulePresentation),
                                         CommonBundle.message("title.error"));
                return;
              }
              if (!ProjectWizardUtil
                 .createDirectoryIfNotExists(IdeBundle.message("directory.project.file.directory", modulePresentation), component.getPath(), true)) {
                Messages.showErrorDialog(ProjectBundle.message("path.0.is.invalid.error.message", component.getPath()), CommonBundle.message("title.error"));
                 return;
              }
              super.doOKAction();
            }
          };
          copyModuleDialog.show();
          if (!copyModuleDialog.isOK()) return;
          final ModifiableRootModel rootModel = ((ModuleConfigurable)namedConfigurable).getModuleEditor().getModifiableRootModel();
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
              return rootModel.getModule().getModuleType();
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
    }

    public void update(final AnActionEvent e) {
      TreePath[] selectionPaths = myTree.getSelectionPaths();
      if (selectionPaths == null || selectionPaths.length != 1) {
        e.getPresentation().setEnabled(false);
      } else {
        e.getPresentation().setEnabled(getSelectedConfugurable() instanceof ModuleConfigurable);
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
}
