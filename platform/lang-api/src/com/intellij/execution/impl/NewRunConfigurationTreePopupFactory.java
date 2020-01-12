// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;

public abstract class NewRunConfigurationTreePopupFactory {
  //This method initializes structure according to actual state just before popup showing
  public abstract void initStructure(@NotNull Project project);

  @NotNull
  public abstract NodeDescriptor getRootElement();

  protected final NodeDescriptor @NotNull [] convertToDescriptors(@NotNull Project project, NodeDescriptor parent, Object[] elements) {
    ArrayList<NodeDescriptor> descriptors = new ArrayList<>();
    for (Object element : elements) {
      descriptors.add(createDescriptor(project, element, parent));
    }
    return descriptors.toArray(NodeDescriptor.EMPTY_ARRAY);
  }

  //This method is supposed to be called just once for each node, the result goes to cache
  public abstract NodeDescriptor[] createChildElements(@NotNull Project project, @NotNull NodeDescriptor nodeDescriptor);

  public Pair<Icon, String> createIconAndText(@NotNull Object element) {
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

  @NotNull
  public final NodeDescriptor createDescriptor(@NotNull Project project,
                                               @NotNull Object element,
                                               @Nullable NodeDescriptor parentDescriptor) {
    return createDescriptor(project, element, parentDescriptor, NodeDescriptor.DEFAULT_WEIGHT);
  }

  @NotNull
  public NodeDescriptor createDescriptor(@NotNull Project project,
                                         @NotNull Object element,
                                         @Nullable NodeDescriptor parentDescriptor,
                                         int weight) {
    if (element instanceof NodeDescriptor) {
      return (NodeDescriptor)element;
    }

    Pair<Icon, String> iconAndText = createIconAndText(element);
    return new NodeDescriptor(project, parentDescriptor) {
      {
        myClosedIcon = iconAndText.first;
        myName = iconAndText.second;
      }

      @Override
      public boolean update() {
        return false;
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
}
