/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.facet.Facet;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureDaemonAnalyzerListener;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.MasterDetailsState;
import com.intellij.openapi.ui.MasterDetailsStateService;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.navigation.Place;
import com.intellij.util.Function;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;

public abstract class BaseStructureConfigurable extends MasterDetailsComponent implements SearchableConfigurable, Disposable, Place.Navigator {

  protected StructureConfigurableContext myContext;

  protected final Project myProject;

  protected boolean myUiDisposed = true;

  private boolean myWasTreeInitialized;

  protected boolean myAutoScrollEnabled = true;

  protected BaseStructureConfigurable(Project project, MasterDetailsState state) {
    super(state);
    myProject = project;
  }

  protected BaseStructureConfigurable(final Project project) {
    myProject = project;
  }

  public void init(StructureConfigurableContext context) {
    myContext = context;
    myContext.getDaemonAnalyzer().addListener(new ProjectStructureDaemonAnalyzerListener() {
      @Override
      public void problemsChanged(@NotNull ProjectStructureElement element) {
        if (!myTree.isShowing()) return;

        myTree.revalidate();
        myTree.repaint();
      }
    });
  }

  @Override
  protected MasterDetailsStateService getStateService() {
    return MasterDetailsStateService.getInstance(myProject);
  }

  @Override
  public ActionCallback navigateTo(@Nullable final Place place, final boolean requestFocus) {
    if (place == null) return ActionCallback.DONE;

    final Object object = place.getPath(TREE_OBJECT);
    final String byName = (String)place.getPath(TREE_NAME);

    if (object == null && byName == null) return ActionCallback.DONE;

    final MyNode node = object == null ? null : findNodeByObject(myRoot, object);
    final MyNode nodeByName = byName == null ? null : findNodeByName(myRoot, byName);

    if (node == null && nodeByName == null) return ActionCallback.DONE;

    final NamedConfigurable config;
    if (node != null) {
      config = node.getConfigurable();
    } else {
      config = nodeByName.getConfigurable();
    }

    final ActionCallback result = new ActionCallback().doWhenDone(new Runnable() {
      @Override
      public void run() {
        myAutoScrollEnabled = true;
      }
    });

    myAutoScrollEnabled = false;
    myAutoScrollHandler.cancelAllRequests();
    final MyNode nodeToSelect = node != null ? node : nodeByName;
    selectNodeInTree(nodeToSelect, requestFocus).doWhenDone(new Runnable() {
      @Override
      public void run() {
        setSelectedNode(nodeToSelect);
        Place.goFurther(config, place, requestFocus).notifyWhenDone(result);
      }
    });

    return result;
  }


  @Override
  public void queryPlace(@NotNull final Place place) {
    if (myCurrentConfigurable != null) {
      place.putPath(TREE_OBJECT, myCurrentConfigurable.getEditableObject());
      Place.queryFurther(myCurrentConfigurable, place);
    }
  }

  @Override
  protected void initTree() {
    if (myWasTreeInitialized) return;
    myWasTreeInitialized = true;

    super.initTree();
    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      @Override
      public String convert(final TreePath treePath) {
        return ((MyNode)treePath.getLastPathComponent()).getDisplayName();
      }
    }, true);
    ToolTipManager.sharedInstance().registerComponent(myTree);
    myTree.setCellRenderer(new ProjectStructureElementRenderer(myContext));
  }

  @Override
  public void disposeUIResources() {
    if (myUiDisposed) return;

    super.disposeUIResources();

    myUiDisposed = true;

    myAutoScrollHandler.cancelAllRequests();

    myContext.getDaemonAnalyzer().clear();

    Disposer.dispose(this);
  }

  public void checkCanApply() throws ConfigurationException {
  }

  protected void addCollapseExpandActions(final List<AnAction> result) {
    final TreeExpander expander = new TreeExpander() {
      @Override
      public void expandAll() {
        TreeUtil.expandAll(myTree);
      }

      @Override
      public boolean canExpand() {
        return true;
      }

      @Override
      public void collapseAll() {
        TreeUtil.collapseAll(myTree, 0);
      }

      @Override
      public boolean canCollapse() {
        return true;
      }
    };
    final CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    result.add(actionsManager.createExpandAllAction(expander, myTree));
    result.add(actionsManager.createCollapseAllAction(expander, myTree));
  }

  @Nullable
  public ProjectStructureElement getSelectedElement() {
    final TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath != null && selectionPath.getLastPathComponent() instanceof MyNode) {
      MyNode node = (MyNode)selectionPath.getLastPathComponent();
      final NamedConfigurable configurable = node.getConfigurable();
      if (configurable instanceof ProjectStructureElementConfigurable) {
        return ((ProjectStructureElementConfigurable)configurable).getProjectStructureElement();
      }
    }
    return null;
  }

  private class MyFindUsagesAction extends FindUsagesInProjectStructureActionBase {

    public MyFindUsagesAction(JComponent parentComponent) {
      super(parentComponent, myProject);
    }

    @Override
    protected boolean isEnabled() {
      final TreePath selectionPath = myTree.getSelectionPath();
      if (selectionPath != null){
        final MyNode node = (MyNode)selectionPath.getLastPathComponent();
        return !node.isDisplayInBold();
      } else {
        return false;
      }
    }

    @Override
    protected StructureConfigurableContext getContext() {
      return myContext;
    }

    @Override
    protected ProjectStructureElement getSelectedElement() {
      return BaseStructureConfigurable.this.getSelectedElement();
    }

    @Override
    protected RelativePoint getPointToShowResults() {
      final int selectedRow = myTree.getSelectionRows()[0];
      final Rectangle rowBounds = myTree.getRowBounds(selectedRow);
      final Point location = rowBounds.getLocation();
      location.x += rowBounds.width;
      return new RelativePoint(myTree, location);
    }
  }


  @Override
  public void reset() {
    myUiDisposed = false;

    if (!myWasTreeInitialized) {
      initTree();
      myTree.setShowsRootHandles(false);
      loadTree();
    } else {
      super.disposeUIResources();
      myTree.setShowsRootHandles(false);
      loadTree();
    }
    for (ProjectStructureElement element : getProjectStructureElements()) {
      myContext.getDaemonAnalyzer().queueUpdate(element);
    }

    super.reset();
  }

  @NotNull
  protected Collection<? extends ProjectStructureElement> getProjectStructureElements() {
    return Collections.emptyList();
  }

  protected abstract void loadTree();
 

  @Override
  @NotNull
  protected ArrayList<AnAction> createActions(final boolean fromPopup) {
    final ArrayList<AnAction> result = new ArrayList<AnAction>();
    AbstractAddGroup addAction = createAddAction();
    if (addAction != null) {
      result.add(addAction);
    }
    result.add(new MyRemoveAction());

    final List<? extends AnAction> copyActions = createCopyActions(fromPopup);
    result.addAll(copyActions);
    result.add(Separator.getInstance());

    if (fromPopup || !Registry.is("ide.new.project.settings")) {
      result.add(new MyFindUsagesAction(myTree));
    }


    return result;
  }

  @NotNull
  protected List<? extends AnAction> createCopyActions(boolean fromPopup) {
    return Collections.emptyList();
  }

  public void onStructureUnselected() {
  }

  public void onStructureSelected() {
  }

  @Nullable
  protected abstract AbstractAddGroup createAddAction();

  protected class MyRemoveAction extends MyDeleteAction {
    public MyRemoveAction() {
      super(new Condition<Object[]>() {
        @Override
        public boolean value(final Object[] objects) {
          Object[] editableObjects = ContainerUtil.mapNotNull(objects, new Function<Object, Object>() {
            @Override
            public Object fun(Object object) {
              if (object instanceof MyNode) {
                final NamedConfigurable namedConfigurable = ((MyNode)object).getConfigurable();
                if (namedConfigurable != null) {
                  return namedConfigurable.getEditableObject();
                }
              }
              return null;
            }
          }, new Object[0]);
          return editableObjects.length == objects.length && canBeRemoved(editableObjects);
        }
      });
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null) return;

      final Set<TreePath> pathsToRemove = new HashSet<TreePath>();
      for (TreePath path : paths) {
        if (removeFromModel(path)) {
          pathsToRemove.add(path);
        }
      }
      removePaths(pathsToRemove.toArray(new TreePath[pathsToRemove.size()]));
    }

    private boolean removeFromModel(final TreePath selectionPath) {
      final Object last = selectionPath.getLastPathComponent();

      if (!(last instanceof MyNode)) return false;

      final MyNode node = (MyNode)last;
      final NamedConfigurable configurable = node.getConfigurable();
      if (configurable == null) return false;
      final Object editableObject = configurable.getEditableObject();

      return removeObject(editableObject);
    }
  }

  protected boolean canBeRemoved(final Object[] editableObjects) {
    for (Object editableObject : editableObjects) {
      if (!canObjectBeRemoved(editableObject)) return false;
    }
    return true;
  }

  private static boolean canObjectBeRemoved(Object editableObject) {
    if (editableObject instanceof Sdk ||
        editableObject instanceof Module ||
        editableObject instanceof Facet ||
        editableObject instanceof Artifact) {
      return true;
    }
    if (editableObject instanceof Library) {
      final LibraryTable table = ((Library)editableObject).getTable();
      return table == null || table.isEditable();
    }
    return false;
  }

  protected boolean removeObject(final Object editableObject) {
    // todo keep only removeModule() and removeFacet() here because other removeXXX() are empty here and overridden in subclasses? Override removeObject() instead?
    if (editableObject instanceof Sdk) {
      removeJdk((Sdk)editableObject);
    }
    else if (editableObject instanceof Module) {
      if (!removeModule((Module)editableObject)) return false;
    }
    else if (editableObject instanceof Facet) {
      if (removeFacet((Facet)editableObject).isEmpty()) return false;
    }
    else if (editableObject instanceof Library) {
      if (!removeLibrary((Library)editableObject)) return false;
    }
    else if (editableObject instanceof Artifact) {
      removeArtifact((Artifact)editableObject);
    }
    return true;
  }

  protected void removeArtifact(Artifact artifact) {
  }


  protected boolean removeLibrary(Library library) {
    return false;
  }

  protected void removeFacetNodes(@NotNull List<Facet> facets) {
    for (Facet facet : facets) {
      MyNode node = findNodeByObject(myRoot, facet);
      if (node != null) {
        removePaths(TreeUtil.getPathFromRoot(node));
      }
    }
  }

  protected List<Facet> removeFacet(final Facet facet) {
    return myContext.myModulesConfigurator.getFacetsConfigurator().removeFacet(facet);
  }

  protected boolean removeModule(final Module module) {
    return true;
  }

  protected void removeJdk(final Sdk editableObject) {
  }

  protected abstract static class AbstractAddGroup extends ActionGroup implements ActionGroupWithPreselection {

    protected AbstractAddGroup(String text, Icon icon) {
      super(text, true);

      final Presentation presentation = getTemplatePresentation();
      presentation.setIcon(icon);

      final Keymap active = KeymapManager.getInstance().getActiveKeymap();
      if (active != null) {
        final Shortcut[] shortcuts = active.getShortcuts("NewElement");
        setShortcutSet(new CustomShortcutSet(shortcuts));
      }
    }

    public AbstractAddGroup(String text) {
      this(text, IconUtil.getAddIcon());
    }

    @Override
    public ActionGroup getActionGroup() {
      return this;
    }

    @Override
    public int getDefaultIndex() {
        return 0;
      }
  }
}
