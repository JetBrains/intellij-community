// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

@ApiStatus.Experimental
public interface ServiceViewContributor<T, Group, State> {
  Topic<ServiceListener> TOPIC = Topic.create("services topic", ServiceListener.class, Topic.BroadcastDirection.TO_CHILDREN);

  @NotNull
  List<T> getNodes(@NotNull Project project);

  List<Group> getGroups(@NotNull T node);

  @NotNull
  State getState(@NotNull T node);

  @NotNull
  ViewDescriptor getGroupDescriptor(@NotNull Group group);

  @NotNull
  ViewDescriptor getNodeDescriptor(@NotNull T node);

  @NotNull
  ViewDescriptor getStateDescriptor(@NotNull State group);

  @Nullable
  default ViewDescriptorRenderer getViewDescriptorRenderer() {
    return null;
  }

  @Nullable
  default SubtreeDescriptor<?> getNodeSubtree(@NotNull T node) { return null; }

  interface SubtreeDescriptor<TT> {

    @NotNull
    List<TT> getItems();

    @NotNull
    ViewDescriptor getItemDescriptor(@NotNull TT item);

    @Nullable
    default SubtreeDescriptor<?> getNodeSubtree(@NotNull TT node) { return null; }
  }

  interface ViewDescriptor {
    JComponent getContentComponent();

    ActionGroup getToolbarActions();

    default ActionGroup getPopupActions() {
      return getToolbarActions();
    }

    ItemPresentation getPresentation();

    DataProvider getDataProvider();

    default void onNodeSelected() {
    }

    default void onNodeUnselected() {
    }

    default boolean handleDoubleClick() {
      return false;
    }
  }

  interface ViewDescriptorRenderer {
    @Nullable
    Component getRendererComponent(JComponent parent,
                                   Object value,
                                   ViewDescriptor viewDescriptor,
                                   boolean selected,
                                   boolean hasFocus);
  }

  interface ServiceListener {
    void handle(@NotNull ServiceEvent event);
  }

  final class ServiceEvent {
    public final EventType type;
    public final Object target;
    public final Class<?> contributorClass;

    public ServiceEvent(@NotNull Class<?> contributorClass) {
      this(EventType.RESET, contributorClass, contributorClass);
    }

    public ServiceEvent(@NotNull EventType type,
                        @NotNull Object target,
                        @NotNull Class<?> contributorClass) {
      this.type = type;
      this.target = target;
      this.contributorClass = contributorClass;
    }
  }

  enum EventType {
    RESET,
    SERVICE_ADDED, SERVICE_REMOVED, SERVICE_CHANGED,
    GROUP_CHANGED,
    SUBTREE_CHANGED, ITEM_CHANGED
  }
}
