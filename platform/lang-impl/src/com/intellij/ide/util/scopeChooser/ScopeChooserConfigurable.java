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

package com.intellij.ide.util.scopeChooser;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.execution.ExecutionBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.NonDefaultProjectConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.search.scope.packageSet.*;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.Icons;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;

/**
 * User: anna
 * Date: 01-Jul-2006
 */
public class ScopeChooserConfigurable extends MasterDetailsComponent implements NonDefaultProjectConfigurable, SearchableConfigurable {
  private static final Icon SCOPES = IconLoader.getIcon("/ide/scopeConfigurable.png");
  private static final Icon SAVE_ICON = IconLoader.getIcon("/runConfigurations/saveTempConfig.png");
  private ScopeChooserConfigurableState myScopesState = new ScopeChooserConfigurableState();
  private final NamedScopesHolder myLocalScopesManager;
  private final NamedScopesHolder mySharedScopesManager;

  private final Project myProject;

  public static ScopeChooserConfigurable getInstance(Project project) {
    // in platform, ScopeChooserConfigurable is registered as a service, not as a top-level configurable
    Factory factory = ServiceManager.getService(project, Factory.class);
    if (factory != null) {
      return factory.getInstance();
    }
    return ShowSettingsUtil.getInstance().findProjectConfigurable(project, ScopeChooserConfigurable.class);
  }

  public ScopeChooserConfigurable(final Project project) {
    myLocalScopesManager = NamedScopeManager.getInstance(project);
    mySharedScopesManager = DependencyValidationManager.getInstance(project);
    myProject = project;

    MasterDetailsStateService.getInstance(project).register("ScopeChooserConfigurable.UI", this);
    initTree();
  }

  protected ArrayList<AnAction> createActions(final boolean fromPopup) {
    final ArrayList<AnAction> result = new ArrayList<AnAction>();
    result.add(new MyAddAction(fromPopup));
    result.add(new MyDeleteAction(new Condition<Object>() {
      public boolean value(final Object o) {
        if (o instanceof MyNode) {
          final Object editableObject = ((MyNode)o).getConfigurable().getEditableObject();
          return editableObject instanceof NamedScope;
        }
        return false;
      }
    }));
    result.add(new MyCopyAction());
    result.add(new MySaveAsAction());
    result.add(new MyMoveAction(ExecutionBundle.message("move.up.action.name"), IconLoader.getIcon("/actions/moveUp.png"), -1));
    result.add(new MyMoveAction(ExecutionBundle.message("move.down.action.name"), IconLoader.getIcon("/actions/moveDown.png"), 1));
    return result;
  }

  public void reset() {
    reloadTree();
    super.reset();
  }


  public void apply() throws ConfigurationException {
    final Set<MyNode> roots = new HashSet<MyNode>();
    roots.add(myRoot);
    checkApply(roots, ProjectBundle.message("rename.message.prefix.scope"), ProjectBundle.message("rename.scope.title"));
    super.apply();
    processScopes();

    loadStateOrder();
  }

  @Override
  protected void checkApply(Set<MyNode> rootNodes, String prefix, String title) throws ConfigurationException {
    super.checkApply(rootNodes, prefix, title);
    final Set<String> predefinedScopes = new HashSet<String>();
    for (CustomScopesProvider scopesProvider : myProject.getExtensions(CustomScopesProvider.CUSTOM_SCOPES_PROVIDER)) {
      for (NamedScope namedScope : scopesProvider.getCustomScopes()) {
        predefinedScopes.add(namedScope.getName());
      }
    }
    for (MyNode rootNode : rootNodes) {
      for (int i = 0; i < rootNode.getChildCount(); i++) {
        final MyNode node = (MyNode)rootNode.getChildAt(i);
        final NamedConfigurable scopeConfigurable = node.getConfigurable();
        final String name = scopeConfigurable.getDisplayName();
        if (predefinedScopes.contains(name)) {
          selectNodeInTree(node);
          throw new ConfigurationException("Scope name equals to predefined one", ProjectBundle.message("rename.scope.title"));
        }
      }
    }
  }

  @Override
  protected PersistentStateComponent<?> getAdditionalSettings() {
    return myScopesState;
  }

  @TestOnly
  public void loadScopesState(ScopeChooserConfigurableState scopesState) {
    myScopesState = scopesState;
  }

  public boolean isModified() {
    if (myRoot.getChildCount() != myScopesState.myOrder.size()) return true;
    for (int i = 0; i < myRoot.getChildCount(); i++) {
      final MyNode node = (MyNode)myRoot.getChildAt(i);
      final ScopeConfigurable scopeConfigurable = (ScopeConfigurable)node.getConfigurable();
      final NamedScope namedScope = scopeConfigurable.getEditableObject();
      if (myScopesState.myOrder.size() <= i) return true;
      final String name = myScopesState.myOrder.get(i);
      if (!Comparing.strEqual(name, namedScope.getName())) return true;
      if (isInitialized(scopeConfigurable)) {
        final NamedScopesHolder holder = scopeConfigurable.getHolder();
        final NamedScope scope = holder.getScope(name);
        if (scope == null) return true;
        if (scopeConfigurable.isModified()) return true;
      }
    }
    return false;
  }

  private void processScopes() {
    final List<NamedScope> localScopes = new ArrayList<NamedScope>();
    final List<NamedScope> sharedScopes = new ArrayList<NamedScope>();
    for (int i = 0; i < myRoot.getChildCount(); i++) {
      final MyNode node = (MyNode)myRoot.getChildAt(i);
      final ScopeConfigurable scopeConfigurable = (ScopeConfigurable)node.getConfigurable();
      final NamedScope namedScope = scopeConfigurable.getScope();
      if (scopeConfigurable.getHolder() == myLocalScopesManager) {
        localScopes.add(namedScope);
      }
      else {
        sharedScopes.add(namedScope);
      }
    }
    myLocalScopesManager.setScopes(localScopes.toArray(new NamedScope[localScopes.size()]));
    mySharedScopesManager.setScopes(sharedScopes.toArray(new NamedScope[sharedScopes.size()]));
  }

  private void reloadTree() {
    myRoot.removeAllChildren();
    loadScopes(mySharedScopesManager);
    loadScopes(myLocalScopesManager);

    if (isModified()) {
      loadStateOrder();
    }


    TreeUtil.sort(myRoot, new Comparator<DefaultMutableTreeNode>() {
      public int compare(final DefaultMutableTreeNode o1, final DefaultMutableTreeNode o2) {
        final int idx1 = myScopesState.myOrder.indexOf(((MyNode)o1).getDisplayName());
        final int idx2 = myScopesState.myOrder.indexOf(((MyNode)o2).getDisplayName());
        return idx1 - idx2;
      }
    });
  }

  private void loadStateOrder() {
    myScopesState.myOrder.clear();
    for (int i = 0; i < myRoot.getChildCount(); i++) {
      myScopesState.myOrder.add(((MyNode)myRoot.getChildAt(i)).getDisplayName());
    }
  }

  private void loadScopes(final NamedScopesHolder holder) {
    final NamedScope[] scopes = holder.getScopes();
    for (NamedScope scope : scopes) {
      if (isPredefinedScope(scope)) continue;
      myRoot.add(new MyNode(new ScopeConfigurable(scope, holder == mySharedScopesManager, myProject, TREE_UPDATER)));
    }
  }

  private boolean isPredefinedScope(final NamedScope scope) {
    return getPredefinedScopes(myProject).contains(scope);
  }

  private static Collection<NamedScope> getPredefinedScopes(Project project) {
    final Collection<NamedScope> result = new ArrayList<NamedScope>();
    result.addAll(NamedScopeManager.getInstance(project).getPredefinedScopes());
    result.addAll(DependencyValidationManager.getInstance(project).getPredefinedScopes());
    return result;
  }

  protected void initTree() {
    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        final TreePath path = e.getOldLeadSelectionPath();
        if (path != null) {
          final MyNode node = (MyNode)path.getLastPathComponent();
          final NamedConfigurable namedConfigurable = node.getConfigurable();
          if (namedConfigurable instanceof ScopeConfigurable) {
            ((ScopeConfigurable)namedConfigurable).cancelCurrentProgress();
          }
        }
      }
    });
    super.initTree();
    myTree.setShowsRootHandles(false);
    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      public String convert(final TreePath treePath) {
        return ((MyNode)treePath.getLastPathComponent()).getDisplayName();
      }
    }, true);
  }

  protected void processRemovedItems() {
    //do nothing
  }

  protected boolean wasObjectStored(Object editableObject) {
    if (editableObject instanceof NamedScope) {
      NamedScope scope = (NamedScope)editableObject;
      final String scopeName = scope.getName();
      return myLocalScopesManager.getScope(scopeName) != null || mySharedScopesManager.getScope(scopeName) != null;
    }
    return false;
  }

  public String getDisplayName() {
    return IdeBundle.message("scopes.display.name");
  }

  public Icon getIcon() {
    return SCOPES;
  }

  @NotNull
  @NonNls
  public String getHelpTopic() {
    return "project.scopes";  //todo help id
  }

  protected void updateSelection(@Nullable final NamedConfigurable configurable) {
    super.updateSelection(configurable);
    if (configurable instanceof ScopeConfigurable) {
      ((ScopeConfigurable)configurable).restoreCanceledProgress();
    }
  }

  protected
  @Nullable
  String getEmptySelectionString() {
    return "Select a scope to view or edit its details here";
  }

  private String createUniqueName() {
    String str = InspectionsBundle.message("inspection.profile.unnamed");
    final HashSet<String> treeScopes = new HashSet<String>();
    obtainCurrentScopes(treeScopes);
    if (!treeScopes.contains(str)) return str;
    int i = 1;
    while (true) {
      if (!treeScopes.contains(str + i)) return str + i;
      i++;
    }
  }

  private void obtainCurrentScopes(final HashSet<String> scopes) {
    for (int i = 0; i < myRoot.getChildCount(); i++) {
      final MyNode node = (MyNode)myRoot.getChildAt(i);
      final NamedScope scope = (NamedScope)node.getConfigurable().getEditableObject();
      scopes.add(scope.getName());
    }
  }

  private void addNewScope(final NamedScope scope, final boolean isLocal) {
    final MyNode nodeToAdd = new MyNode(new ScopeConfigurable(scope, !isLocal, myProject, TREE_UPDATER));
    myRoot.add(nodeToAdd);
    ((DefaultTreeModel)myTree.getModel()).reload(myRoot);
    selectNodeInTree(nodeToAdd);
  }

  private void createScope(final boolean isLocal, String title, final PackageSet set) {
    final String newName = Messages.showInputDialog(myTree, IdeBundle.message("add.scope.name.label"), title,
                                                    Messages.getInformationIcon(), createUniqueName(), new InputValidator() {
      public boolean checkInput(String inputString) {
        final NamedScopesHolder holder = isLocal ? myLocalScopesManager : mySharedScopesManager;
        for (NamedScope scope : holder.getPredefinedScopes()) {
          if (Comparing.strEqual(scope.getName(), inputString.trim())) {
            return false;
          }
        }
        return inputString.trim().length() > 0;
      }

      public boolean canClose(String inputString) {
        return checkInput(inputString);
      }
    });
    if (newName != null) {
      final NamedScope scope = new NamedScope(newName, set);
      addNewScope(scope, isLocal);
    }
  }

  @NonNls
  public String getId() {
    return getHelpTopic();
  }

  @Nullable
  public Runnable enableSearch(final String option) {
    return null;
  }

  private class MyAddAction extends ActionGroup implements ActionGroupWithPreselection {

    private AnAction[] myChildren;
    private final boolean myFromPopup;

    public MyAddAction(boolean fromPopup) {
      super(IdeBundle.message("add.scope.popup.title"), true);
      myFromPopup = fromPopup;
      final Presentation presentation = getTemplatePresentation();
      presentation.setIcon(Icons.ADD_ICON);
      setShortcutSet(CommonShortcuts.INSERT);
    }


    public void update(AnActionEvent e) {
      super.update(e);
      if (myFromPopup) {
        setPopup(false);
      }
    }

    @NotNull
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      if (myChildren == null) {
        myChildren = new AnAction[2];
        myChildren[0] = new AnAction(IdeBundle.message("add.local.scope.action.text"), IdeBundle.message("add.local.scope.action.text"),
                                     myLocalScopesManager.getIcon()) {
          public void actionPerformed(AnActionEvent e) {
            createScope(true, IdeBundle.message("add.scope.dialog.title"), null);
          }
        };
        myChildren[1] = new AnAction(IdeBundle.message("add.shared.scope.action.text"), IdeBundle.message("add.shared.scope.action.text"),
                                     mySharedScopesManager.getIcon()) {
          public void actionPerformed(AnActionEvent e) {
            createScope(false, IdeBundle.message("add.scope.dialog.title"), null);
          }
        };
      }
      if (myFromPopup) {
        final AnAction action = myChildren[getDefaultIndex()];
        action.getTemplatePresentation().setIcon(Icons.ADD_ICON);
        return new AnAction[]{action};
      }
      return myChildren;
    }

    public ActionGroup getActionGroup() {
      return this;
    }

    public int getDefaultIndex() {
      final TreePath selectionPath = myTree.getSelectionPath();
      if (selectionPath != null) {
        final MyNode node = (MyNode)selectionPath.getLastPathComponent();
        Object editableObject = node.getConfigurable().getEditableObject();
        if (editableObject instanceof NamedScope) {
          editableObject = ((MyNode)node.getParent()).getConfigurable().getEditableObject();
        }
        if (editableObject instanceof NamedScopeManager) {
          return 0;
        }
        else if (editableObject instanceof DependencyValidationManager) {
          return 1;
        }
      }
      return 0;
    }
  }


  private class MyMoveAction extends AnAction {
    private final int myDirection;

    protected MyMoveAction(String text, Icon icon, int direction) {
      super(text, text, icon);
      myDirection = direction;
    }

    public void actionPerformed(final AnActionEvent e) {
      TreeUtil.moveSelectedRow(myTree, myDirection);
    }

    public void update(final AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(false);
      final TreePath selectionPath = myTree.getSelectionPath();
      if (selectionPath != null) {
        final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
        if (treeNode.getUserObject() instanceof ScopeConfigurable) {
          if (myDirection < 0) {
            presentation.setEnabled(treeNode.getPreviousSibling() != null);
          }
          else {
            presentation.setEnabled(treeNode.getNextSibling() != null);
          }
        }
      }
    }
  }

  private class MyCopyAction extends AnAction {
    public MyCopyAction() {
      super(ExecutionBundle.message("copy.configuration.action.name"), ExecutionBundle.message("copy.configuration.action.name"),
            COPY_ICON);
      registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_MASK)), myTree);
    }

    public void actionPerformed(AnActionEvent e) {
      NamedScope scope = (NamedScope)getSelectedObject();
      if (scope != null) {
        final NamedScope newScope = scope.createCopy();
        final ScopeConfigurable configurable =
          (ScopeConfigurable)((MyNode)myTree.getSelectionPath().getLastPathComponent()).getConfigurable();
        addNewScope(new NamedScope(createUniqueName(), newScope.getValue()), configurable.getHolder() == myLocalScopesManager);
      }
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(getSelectedObject() instanceof NamedScope);
    }
  }

  private class MySaveAsAction extends AnAction {
    public MySaveAsAction() {
      super(ExecutionBundle.message("action.name.save.as.configuration"), ExecutionBundle.message("action.name.save.as.configuration"),
            SAVE_ICON);
    }

    public void actionPerformed(AnActionEvent e) {
      final TreePath selectionPath = myTree.getSelectionPath();
      if (selectionPath != null) {
        final MyNode node = (MyNode)selectionPath.getLastPathComponent();
        final NamedConfigurable configurable = node.getConfigurable();
        if (configurable instanceof ScopeConfigurable) {
          final ScopeConfigurable scopeConfigurable = (ScopeConfigurable)configurable;
          PackageSet set = scopeConfigurable.getEditableObject().getValue();
          if (set != null) {
            if (scopeConfigurable.getHolder() == mySharedScopesManager) {
              createScope(false, IdeBundle.message("scopes.save.dialog.title.shared"), set.createCopy());
            }
            else {
              createScope(true, IdeBundle.message("scopes.save.dialog.title.local"), set.createCopy());
            }
          }
        }
      }
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(getSelectedObject() instanceof NamedScope);
    }
  }

  // workaround: ScopeChooserConfigurable is a MasterDetailsComponent which is a PersistentStateComponent,
  // but it doesn't have (and doesn't need) a @State annotation. so we get an exception if we load it as a service
  // directly
  public static class Factory {
    private ScopeChooserConfigurable myConfigurable;

    public Factory(Project project) {
      myConfigurable = new ScopeChooserConfigurable(project);
    }

    public ScopeChooserConfigurable getInstance() {
      return myConfigurable;
    }
  }

  public static class ScopeChooserConfigurableState implements PersistentStateComponent<ScopeChooserConfigurableState> {
    @Tag("order")
    @AbstractCollection(surroundWithTag = false, elementTag = "scope", elementValueAttribute = "name")
    public List<String> myOrder = new ArrayList<String>();

    public ScopeChooserConfigurableState getState() {
      return this;
    }

    public void loadState(ScopeChooserConfigurableState state) {
      XmlSerializerUtil.copyBean(state, this);
    }
  }
}
