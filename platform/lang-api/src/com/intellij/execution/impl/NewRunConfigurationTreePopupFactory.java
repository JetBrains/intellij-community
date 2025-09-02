// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;

public abstract class NewRunConfigurationTreePopupFactory {
  //This method initializes structure according to actual state just before popup showing
  public abstract void initStructure(@NotNull Project project);

  public abstract @NotNull NodeDescriptor getRootElement();

  protected final NodeDescriptor @NotNull [] convertToDescriptors(@NotNull Project project, NodeDescriptor parent, Object[] elements) {
    ArrayList<NodeDescriptor> descriptors = new ArrayList<>();
    for (Object element : elements) {
      descriptors.add(createDescriptor(project, element, parent));
    }
    return descriptors.toArray(NodeDescriptor.getEmptyArray());
  }

  //This method is supposed to be called just once for each node, the result goes to cache
  public abstract NodeDescriptor[] createChildElements(@NotNull Project project, @NotNull NodeDescriptor nodeDescriptor);

  public Pair<Icon, @Nls String> createIconAndText(@NotNull Object element) {
    if (element instanceof ConfigurationFactory) {
      return Pair.create(((ConfigurationFactory)element).getIcon(), ((ConfigurationFactory)element).getName());
    }
    else if (element instanceof ConfigurationType) {
      return Pair.create(((ConfigurationType)element).getIcon(), ((ConfigurationType)element).getDisplayName());
    }
    else {
      return Pair.create(null, String.valueOf(element));
    }
  }

  public final @NotNull NodeDescriptor createDescriptor(@NotNull Project project,
                                                        @NotNull Object element,
                                                        @Nullable NodeDescriptor parentDescriptor) {
    return createDescriptor(project, element, parentDescriptor, NodeDescriptor.getDefaultWeight());
  }

  public @NotNull NodeDescriptor createDescriptor(@NotNull Project project,
                                                  @NotNull Object element,
                                                  @Nullable NodeDescriptor parentDescriptor,
                                                  int weight) {
    if (element instanceof NodeDescriptor) {
      return (NodeDescriptor)element;
    }

    Pair<Icon, @Nls String> iconAndText = createIconAndText(element);
    SimpleTextAttributes attributes =
      (!project.isDefault() && DumbService.getInstance(project).isDumb() && !isEditableInDumbMode(element)) ?
      SimpleTextAttributes.GRAYED_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES;
    return new PresentableNodeDescriptor<>(project, parentDescriptor) {
      @Override
      protected void update(@NotNull PresentationData presentation) {
        presentation.addText(iconAndText.second, attributes);
        presentation.setIcon(iconAndText.first);
      }

      @Override
      public @NlsSafe String toString() {
        return iconAndText.second;
      }

      @Override
      public int getWeight() {
        return weight;
      }

      @Override
      public Object getElement() {
        return element;
      }
    };
  }

  @ApiStatus.Internal
  public static boolean isEditableInDumbMode(@NotNull Object element) {
    if (element instanceof ConfigurationFactory) {
      return ((ConfigurationFactory)element).isEditableInDumbMode();
    }
    if (element instanceof ConfigurationType) {
      return ConfigurationTypeUtil.isEditableInDumbMode((ConfigurationType)element);
    }
    return false;
  }
}
