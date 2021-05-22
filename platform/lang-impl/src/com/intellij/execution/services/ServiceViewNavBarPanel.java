// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.execution.services.ServiceModel.ServiceNode;
import com.intellij.execution.services.ServiceModel.ServiceViewItem;
import com.intellij.ide.navigationToolbar.NavBarModel;
import com.intellij.ide.navigationToolbar.NavBarModelExtension;
import com.intellij.ide.navigationToolbar.NavBarModelListener;
import com.intellij.ide.navigationToolbar.NavBarPanel;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

final class ServiceViewNavBarPanel extends NavBarPanel {
  private final Consumer<ServiceViewItem> mySelector;
  private boolean myRebuildNeeded = true;

  ServiceViewNavBarPanel(@NotNull Project project, boolean docked, @NotNull ServiceViewModel viewModel,
                         @NotNull Consumer<ServiceViewItem> selector) {
    super(project, docked);
    mySelector = selector;
    Disposer.register(viewModel, this);
    ((ServiceViewNavBarModel)getModel()).setViewModel(viewModel);
  }

  @Override
  protected boolean isDisposeOnRemove() {
    return false;
  }

  @Override
  protected NavBarModel createModel() {
    return new ServiceViewNavBarModel(myProject, new NavBarModelListener() {
      @Override
      public void modelChanged() {
        myRebuildNeeded = true;
        getUpdateQueue().queueRebuildUi();
      }

      @Override
      public void selectionChanged() {
        updateItems();
        scrollSelectionToVisible();
      }
    });
  }

  @Override
  public boolean isRebuildUiNeeded() {
    if (myRebuildNeeded) {
      myRebuildNeeded = false;
      return true;
    }
    return super.isRebuildUiNeeded();
  }

  @Override
  protected void doubleClick(Object object) {
    if (object instanceof ServiceViewItem) {
      mySelector.accept((ServiceViewItem)object);
    }
    hideHint(true);
  }

  void hidePopup() {
    hideHint(false);
  }

  private static final class ServiceViewNavBarModel extends NavBarModel {
    private ServiceViewModel myViewModel;
    private final ServiceViewNavBarRoot myRoot = new ServiceViewNavBarRoot();

    ServiceViewNavBarModel(@NotNull Project project, @NotNull NavBarModelListener notificator) {
      super(project, notificator, null);
    }

    void setViewModel(@NotNull ServiceViewModel viewModel) {
      myViewModel = viewModel;
    }

    @Override
    public void updateModel(DataContext dataContext) {
    }

    @Override
    public void updateModelAsync(DataContext dataContext, @Nullable Runnable callback) {
    }

    @Override
    protected void updateModel(PsiElement psiElement, NavBarModelExtension ownerExtension) {
    }

    @Override
    public void updateModel(Object object) {
      List<Object> path = new ArrayList<>();
      if (object instanceof ServiceViewItem) {
        ServiceViewItem item = (ServiceViewItem)object;
        List<? extends ServiceViewItem> roots = myViewModel.getVisibleRoots();

        do {
          path.add(item);
          item = roots.contains(item) ? null : item.getParent();
        }
        while (item != null);
      }
      path.add(myRoot);
      Collections.reverse(path);
      setModel(path, true);
    }

    @Override
    protected boolean hasChildren(Object object) {
      return !getChildren(object).isEmpty();
    }

    @Override
    protected List<Object> getChildren(Object object) {
      if (object == myRoot) {
        return new ArrayList<>(myViewModel.getVisibleRoots());
      }
      if (object instanceof ServiceViewItem) {
        if (object instanceof ServiceNode) {
          ServiceNode service = (ServiceNode)object;
          if (service.getProvidingContributor() != null && !service.isChildrenInitialized()) {
            myViewModel.getInvoker().invoke(() -> {
              service.getChildren(); // initialize children on background thread
            });
            return Collections.emptyList();
          }
        }
        return new ArrayList<>(myViewModel.getChildren((ServiceViewItem)object));
      }
      return Collections.emptyList();
    }
  }

  static final class ServiceViewNavBarRoot {
  }
}
