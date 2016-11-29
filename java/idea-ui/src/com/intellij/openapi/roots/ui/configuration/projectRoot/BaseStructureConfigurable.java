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
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureDaemonAnalyzerListener;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.MasterDetailsState;
import com.intellij.openapi.ui.MasterDetailsStateService;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.navigation.Place;
import com.intellij.util.Function;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.*;
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

    final ActionCallback result = new ActionCallback().doWhenDone(() -> myAutoScrollEnabled = true);

    myAutoScrollEnabled = false;
    myAutoScrollHandler.cancelAllRequests();
    final MyNode nodeToSelect = node != null ? node : nodeByName;
    selectNodeInTree(nodeToSelect, requestFocus).doWhenDone(() -> {
      setSelectedNode(nodeToSelect);
      Place.goFurther(config, place, requestFocus).notifyWhenDone(result);
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
    final ArrayList<AnAction> result = new ArrayList<>();
    AbstractAddGroup addAction = createAddAction();
    if (addAction != null) {
      result.add(addAction);
    }
    result.add(new MyRemoveAction());

    final List<? extends AnAction> copyActions = createCopyActions(fromPopup);
    result.addAll(copyActions);
    result.add(Separator.getInstance());

    if (fromPopup) {
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

  protected List<? extends RemoveConfigurableHandler<?>> getRemoveHandlers() {
    return Collections.emptyList();
  }

  @NotNull
  private MultiMap<RemoveConfigurableHandler, MyNode> groupNodes(List<MyNode> nodes) {
    List<? extends RemoveConfigurableHandler<?>> handlers = getRemoveHandlers();
    MultiMap<RemoveConfigurableHandler, MyNode> grouped = new LinkedMultiMap<>();
    for (MyNode node : nodes) {
      final NamedConfigurable<?> configurable = node.getConfigurable();
      if (configurable == null) continue;
      RemoveConfigurableHandler handler = findHandler(handlers, configurable.getClass());
      if (handler == null) continue;

      grouped.putValue(handler, node);
    }
    return grouped;
  }

  private static RemoveConfigurableHandler<?> findHandler(List<? extends RemoveConfigurableHandler<?>> handlers,
                                                          Class<? extends NamedConfigurable> configurableClass) {
    for (RemoveConfigurableHandler<?> handler : handlers) {
      if (handler.getConfigurableClass().isAssignableFrom(configurableClass)) {
        return handler;
      }
    }
    return null;
  }

  protected class MyRemoveAction extends MyDeleteAction {
    public MyRemoveAction() {
      //noinspection Convert2Lambda
      super(new Condition<Object[]>() {
        @Override
        public boolean value(final Object[] objects) {
          List<MyNode> nodes = new ArrayList<>();
          for (Object object : objects) {
            if (!(object instanceof MyNode)) return false;
            nodes.add((MyNode)object);
          }
          MultiMap<RemoveConfigurableHandler, MyNode> map = groupNodes(nodes);
          for (Map.Entry<RemoveConfigurableHandler, Collection<MyNode>> entry : map.entrySet()) {
            //noinspection unchecked
            if (!entry.getKey().canBeRemoved(getEditableObjects(entry.getValue()))) {
              return false;
            }
          }
          return true;
        }
      });
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null) return;

      List<MyNode> removedNodes = removeFromModel(paths);
      removeNodes(removedNodes);
    }

    private List<MyNode> removeFromModel(final TreePath[] paths) {
      List<MyNode> nodes = ContainerUtil.mapNotNull(paths, path -> {
        Object node = path.getLastPathComponent();
        return node instanceof MyNode ? (MyNode)node : null;
      });
      MultiMap<RemoveConfigurableHandler, MyNode> grouped = groupNodes(nodes);

      List<MyNode> removedNodes = new ArrayList<>();
      for (Map.Entry<RemoveConfigurableHandler, Collection<MyNode>> entry : grouped.entrySet()) {
        //noinspection unchecked
        boolean removed = entry.getKey().remove(getEditableObjects(entry.getValue()));
        if (removed) {
          removedNodes.addAll(entry.getValue());
        }
      }
      return removedNodes;
    }
  }

  private static List<?> getEditableObjects(Collection<MyNode> value) {
    List<Object> objects = new ArrayList<>();
    for (MyNode node : value) {
      objects.add(node.getConfigurable().getEditableObject());
    }
    return objects;
  }


  protected void removeFacetNodes(@NotNull List<Facet> facets) {
    for (Facet facet : facets) {
      MyNode node = findNodeByObject(myRoot, facet);
      if (node != null) {
        removePaths(TreeUtil.getPathFromRoot(node));
      }
    }
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
