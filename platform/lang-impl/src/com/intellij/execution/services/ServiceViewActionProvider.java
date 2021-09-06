// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.execution.services.ServiceModel.ServiceViewItem;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarSpacer;
import com.intellij.openapi.project.Project;
import com.intellij.ui.PopupHandler;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.tree.TreeModelAdapter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.List;

import static com.intellij.execution.services.ServiceViewDragHelper.getTheOnlyRootContributor;

class ServiceViewActionProvider {
  @NonNls private static final String SERVICE_VIEW_ITEM_TOOLBAR = "ServiceViewItemToolbar";
  @NonNls static final String SERVICE_VIEW_ITEM_POPUP = "ServiceViewItemPopup";
  @NonNls private static final String SERVICE_VIEW_TREE_TOOLBAR = "ServiceViewTreeToolbar";

  private static final ServiceViewActionProvider ourInstance = new ServiceViewActionProvider();

  static ServiceViewActionProvider getInstance() {
    return ourInstance;
  }

  ActionToolbar createServiceToolbar(@NotNull JComponent component) {
    ActionGroup actions = (ActionGroup)ActionManager.getInstance().getAction(SERVICE_VIEW_ITEM_TOOLBAR);
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.SERVICES_TOOLBAR, actions, false);
    toolbar.setTargetComponent(component);
    return toolbar;
  }

  JComponent wrapServiceToolbar(@NotNull ActionToolbar toolbar) {
    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(toolbar.getComponent(), BorderLayout.CENTER);
    toolbar.getComponent().addComponentListener(new ComponentListener() {
      @Override
      public void componentResized(ComponentEvent e) {
      }

      @Override
      public void componentMoved(ComponentEvent e) {
      }

      @Override
      public void componentShown(ComponentEvent e) {
        wrapper.setVisible(true);
      }

      @Override
      public void componentHidden(ComponentEvent e) {
        wrapper.setVisible(false);
      }
    });
    wrapper.add(new ActionToolbarSpacer(false), BorderLayout.SOUTH);
    return wrapper;
  }

  void installPopupHandler(@NotNull JComponent component) {
    PopupHandler.installPopupMenu(component, SERVICE_VIEW_ITEM_POPUP, ActionPlaces.SERVICES_POPUP);
  }

  ActionToolbar createMasterComponentToolbar(@NotNull JComponent component) {
    DefaultActionGroup group = new DefaultActionGroup();

    if (component instanceof JTree) {
      TreeExpander treeExpander = new ServiceViewTreeExpander((JTree)component);
      AnAction expandAllAction = CommonActionsManager.getInstance().createExpandAllAction(treeExpander, component);
      group.add(expandAllAction);
      AnAction collapseAllAction = CommonActionsManager.getInstance().createCollapseAllAction(treeExpander, component);
      group.add(collapseAllAction);
      group.addSeparator();
    }

    group.addSeparator();
    AnAction treeActions = ActionManager.getInstance().getAction(SERVICE_VIEW_TREE_TOOLBAR);
    treeActions.registerCustomShortcutSet(component, null);
    group.add(treeActions);

    ActionToolbar treeActionsToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.SERVICES_TOOLBAR, group, true);
    treeActionsToolBar.setTargetComponent(component);

    return treeActionsToolBar;
  }

  List<AnAction> getAdditionalGearActions() {
    AnAction showServicesActions = ActionManager.getInstance().getAction("ServiceView.ShowServices");
    return showServicesActions == null ? Collections.emptyList() : Collections.singletonList(showServicesActions);
  }

  @Nullable
  static ServiceView getSelectedView(@NotNull AnActionEvent e) {
    return getSelectedView(e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT));
  }

  @Nullable
  static ServiceView getSelectedView(@NotNull DataProvider provider) {
    return getSelectedView(ObjectUtils.tryCast(provider.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT.getName()), Component.class));
  }

  @Nullable
  private static ServiceView getSelectedView(@Nullable Component contextComponent) {
    while (contextComponent != null && !(contextComponent instanceof ServiceView)) {
      contextComponent = contextComponent.getParent();
    }
    return (ServiceView)contextComponent;
  }

  private static class ServiceViewTreeExpander extends DefaultTreeExpander {
    private boolean myFlat;

    ServiceViewTreeExpander(JTree tree) {
      super(tree);
      TreeModelListener listener = new TreeModelAdapter() {
        @Override
        protected void process(@NotNull TreeModelEvent event, @NotNull EventType type) {
          myFlat = isFlat(tree.getModel());
        }
      };
      tree.getModel().addTreeModelListener(listener);
      PropertyChangeListener propertyChangeListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent event) {
          Object oldValue = event.getOldValue();
          if (oldValue instanceof TreeModel) {
            ((TreeModel)oldValue).removeTreeModelListener(listener);
          }
          Object newValue = event.getNewValue();
          if (newValue instanceof TreeModel) {
            ((TreeModel)newValue).addTreeModelListener(listener);
          }
        }
      };
      tree.addPropertyChangeListener(JTree.TREE_MODEL_PROPERTY, propertyChangeListener);
    }

    @Override
    public boolean canExpand() {
      return super.canExpand() && !myFlat;
    }

    @Override
    public boolean canCollapse() {
      return super.canCollapse() && !myFlat;
    }

    private static boolean isFlat(TreeModel treeModel) {
      Object root = treeModel.getRoot();
      if (root == null) return false;

      int childCount = treeModel.getChildCount(root);
      for (int i = 0; i < childCount; i++) {
        Object child = treeModel.getChild(root, i);
        if (!treeModel.isLeaf(child)) {
          return false;
        }
      }
      return true;
    }
  }

  private static AnAction @NotNull [] doGetActions(@Nullable AnActionEvent e, boolean toolbar) {
    if (e == null) return AnAction.EMPTY_ARRAY;

    Project project = e.getProject();
    if (project == null) return AnAction.EMPTY_ARRAY;

    ServiceView serviceView = getSelectedView(e);
    if (serviceView == null) return AnAction.EMPTY_ARRAY;

    List<ServiceViewItem> selectedItems = serviceView.getSelectedItems();
    if (selectedItems.isEmpty()) return AnAction.EMPTY_ARRAY;

    ServiceViewDescriptor descriptor;
    if (selectedItems.size() == 1) {
      descriptor = selectedItems.get(0).getViewDescriptor();
    }
    else {
      ServiceViewContributor<?> contributor = getTheOnlyRootContributor(selectedItems);
      descriptor = contributor == null ? null : contributor.getViewDescriptor(project);
    }
    if (descriptor == null) return AnAction.EMPTY_ARRAY;

    ActionGroup group = toolbar ? descriptor.getToolbarActions() : descriptor.getPopupActions();
    return group == null ? AnAction.EMPTY_ARRAY : group.getChildren(e);
  }

  public static class ItemToolbarActionGroup extends ActionGroup {
    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      return doGetActions(e, true);
    }
  }

  public static class ItemPopupActionGroup extends ActionGroup {
    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      return doGetActions(e, false);
    }
  }
}