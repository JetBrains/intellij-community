// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.concurrency.InvokerSupplier;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

class ServiceModel implements Disposable, InvokerSupplier {
  private static final ExtensionPointName<ServiceViewContributor> EP_NAME = ExtensionPointName.create("com.intellij.serviceViewContributor");

  private final Project myProject;
  private final Invoker myInvoker = new Invoker.BackgroundThread(this);
  private final List<ServiceViewItem> myRoots = new CopyOnWriteArrayList<>();
  private volatile boolean myRootsInitialized;

  ServiceModel(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void dispose() {
  }

  @NotNull
  @Override
  public Invoker getInvoker() {
    return myInvoker;
  }

  @NotNull
  List<? extends ServiceViewItem> getRoots() {
    return myRootsInitialized ? myRoots : Collections.emptyList();
  }

  CancellablePromise<?> initRoots() {
    return getInvoker().runOrInvokeLater(() -> {
      if (!myRootsInitialized) {
        myRoots.addAll(doGetRoots());
        myRootsInitialized = true;
      }
    });
  }

  private List<? extends ServiceViewItem> doGetRoots() {
    List<ServiceViewItem> result = new ArrayList<>();
    for (ServiceViewContributor<?> contributor : getContributors()) {
      ContributorNode root = new ContributorNode(myProject, contributor);
      if (!root.getChildren().isEmpty()) {
        result.add(root);
      }
    }
    return result;
  }

  @Nullable
  ServiceViewItem findItemById(List<String> ids, ServiceViewContributor contributor) {
    if (ids.isEmpty()) return null;

    List<? extends ServiceViewItem> roots = ContainerUtil.filter(getRoots(), item -> contributor.equals(item.getContributor()));
    if (roots.isEmpty()) return null;

    return findItemById(new LinkedList<>(ids), roots);
  }

  private static ServiceViewItem findItemById(Deque<String> path, List<? extends ServiceViewItem> roots) {
    String id = path.removeFirst();
    for (ServiceViewItem root : roots) {
      if (id.equals(root.getViewDescriptor().getId())) {
        return path.isEmpty() ? root : findItemById(path, root.getChildren());
      }
    }
    return null;
  }

  @NotNull
  CancellablePromise<?> refresh(@NotNull ServiceEventListener.ServiceEvent e) {
    return getInvoker().runOrInvokeLater(() -> reset(e.contributorClass));
  }

  private void reset(Class<?> contributorClass) {
    int startIndex = -1;

    if (myRoots.isEmpty()) {
      startIndex = 0;
    }
    else {
      ServiceViewItem contributorNode = null;
      for (int i = 0; i < myRoots.size(); i++) {
        ServiceViewItem child = myRoots.get(i);
        if (contributorClass.isInstance(child.getContributor())) {
          contributorNode = child;
          startIndex = i;
          break;
        }
      }
      if (contributorNode != null) {
        myRoots.remove(contributorNode);
      }
      else {
        ServiceViewContributor[] contributors = getContributors();
        List<ServiceViewContributor> existingContributors = ContainerUtil.map(myRoots, ServiceViewItem::getContributor);
        for (int i = contributors.length - 1; i >= 0; i--) {
          ServiceViewContributor contributor = contributors[i];
          if (!contributorClass.isInstance(contributor)) {
            startIndex = existingContributors.indexOf(contributor);
            if (startIndex == 0) {
              break;
            }
          }
          else {
            break;
          }
        }
        if (startIndex < 0) {
          startIndex = myRoots.size();
        }
      }
    }

    ServiceViewItem newRoot = null;
    for (ServiceViewContributor<?> contributor : getContributors()) {
      if (contributorClass.isInstance(contributor)) {
        newRoot = new ContributorNode(myProject, contributor);
        if (newRoot.getChildren().isEmpty()) {
          newRoot = null;
        }
        break;
      }
    }
    if (newRoot != null) {
      myRoots.add(startIndex, newRoot);
    }
  }

  @NotNull
  public static ServiceViewContributor[] getContributors() {
    ServiceViewContributor[] result = EP_NAME.getExtensions();
    return Registry.is("ide.service.view") ?
           result :
           Arrays.stream(result).filter(c -> c.alwaysEnabled()).toArray(ServiceViewContributor[]::new);
  }

  private static <T> List<ServiceViewItem> getContributorChildren(Project project,
                                                                  ServiceViewItem parent,
                                                                  ServiceViewContributor<T> contributor) {
    Set<ServiceViewItem> children = new LinkedHashSet<>();
    Map<Object, ServiceGroupNode> groupNodes = new HashMap<>();
    for (T service : contributor.getServices(project)) {
      Object value = service instanceof ServiceViewProvidingContributor ? ((ServiceViewProvidingContributor)service).asService() : service;

      if (contributor instanceof ServiceViewGroupingContributor) {
        ServiceViewGroupingContributor<T, Object> groupingContributor = (ServiceViewGroupingContributor<T, Object>)contributor;
        Object group = groupingContributor.groupBy(service);
        if (group != null) {
          ServiceGroupNode groupNode = groupNodes.get(group);
          if (groupNode == null) {
            groupNode = new ServiceGroupNode(group, parent, contributor, groupingContributor.getGroupDescriptor(group));
            groupNodes.put(group, groupNode);
          }
          ServiceViewItem
            serviceNode = new ServiceNode(value, groupNode, contributor, contributor.getServiceDescriptor(service), project,
                                          service instanceof ServiceViewContributor ? (ServiceViewContributor)service : null);
          groupNode.getChildren().add(serviceNode);
          children.add(groupNode);
          continue;
        }
      }

      ServiceViewItem
        serviceNode = new ServiceNode(value, parent, contributor, contributor.getServiceDescriptor(service), project,
                                      service instanceof ServiceViewContributor ? (ServiceViewContributor)service : null);
      children.add(serviceNode);
    }
    return new ArrayList<>(children);
  }

  abstract static class ServiceViewItem {
    private final Object myValue;
    private final ServiceViewItem myParent;
    private final ServiceViewContributor myContributor;
    private final ServiceViewDescriptor myViewDescriptor;
    private List<ServiceViewItem> myChildren;
    private boolean myPresentationUpdated;

    protected ServiceViewItem(@NotNull Object value, @Nullable ServiceViewItem parent, @NotNull ServiceViewContributor contributor,
                              @NotNull ServiceViewDescriptor viewDescriptor) {
      myValue = value;
      myParent = parent;
      myContributor = contributor;
      myViewDescriptor = viewDescriptor;
    }

    @NotNull
    Object getValue() {
      return myValue;
    }

    @NotNull
    ServiceViewContributor getContributor() {
      return myContributor;
    }

    @NotNull
    ServiceViewContributor getRootContributor() {
      return myParent == null ? myContributor : myParent.getRootContributor();
    }

    @NotNull
    ServiceViewDescriptor getViewDescriptor() {
      if (!myPresentationUpdated) {
        myPresentationUpdated = true;
        if (myValue instanceof NodeDescriptor) {
          ((NodeDescriptor)myValue).update();
        }
      }
      return myViewDescriptor;
    }

    @Nullable
    ServiceViewItem getParent() {
      return myParent;
    }

    @NotNull
    List<ServiceViewItem> getChildren() {
      if (myChildren == null) {
        myChildren = doGetChildren();
      }
      return myChildren;
    }

    @NotNull
    abstract List<ServiceViewItem> doGetChildren();

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ServiceViewItem node = (ServiceViewItem)o;
      return myValue.equals(node.myValue);
    }

    @Override
    public int hashCode() {
      return myValue.hashCode();
    }
  }

  static class ContributorNode extends ServiceViewItem {
    private final Project myProject;

    ContributorNode(@NotNull Project project, @NotNull ServiceViewContributor contributor) {
      super(contributor, null, contributor, contributor.getViewDescriptor());
      myProject = project;
    }

    @NotNull
    @Override
    List<ServiceViewItem> doGetChildren() {
      return getContributorChildren(myProject, this, (ServiceViewContributor<?>)getContributor());
    }
  }

  static class ServiceNode extends ServiceViewItem {
    private final Project myProject;
    private final ServiceViewContributor<?> myProvidingContributor;

    ServiceNode(@NotNull Object service, @Nullable ServiceViewItem parent, @NotNull ServiceViewContributor contributor,
                @NotNull ServiceViewDescriptor viewDescriptor,
                @NotNull Project project, @Nullable ServiceViewContributor providingContributor) {
      super(service, parent, contributor, viewDescriptor);
      myProject = project;
      myProvidingContributor = providingContributor;
    }

    @NotNull
    @Override
    List<ServiceViewItem> doGetChildren() {
      return myProvidingContributor == null ? Collections.emptyList() : getContributorChildren(myProject, this, myProvidingContributor);
    }
  }

  static class ServiceGroupNode extends ServiceViewItem {
    ServiceGroupNode(@NotNull Object group, @Nullable ServiceViewItem parent, @NotNull ServiceViewContributor contributor,
                     @NotNull ServiceViewDescriptor viewDescriptor) {
      super(group, parent, contributor, viewDescriptor);
    }

    @NotNull
    @Override
    List<ServiceViewItem> doGetChildren() {
      return new ArrayList<>();
    }
  }
}
