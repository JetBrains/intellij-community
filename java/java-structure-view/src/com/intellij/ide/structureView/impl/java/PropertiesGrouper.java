// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.psi.PsiElement;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class PropertiesGrouper implements Grouper {
  public static final @NonNls String ID = "SHOW_PROPERTIES";

  @Override
  public @NotNull Collection<Group> group(@NotNull AbstractTreeNode<?> parent, @NotNull Collection<TreeElement> children) {
    if (parent.getValue() instanceof PropertyGroup) return Collections.emptyList();
    Map<Group,Group> result = new HashMap<>();
    for (TreeElement o : children) {
      if (o instanceof JavaClassTreeElementBase) {
        PsiElement element = ((JavaClassTreeElementBase<?>)o).getElement();
        PropertyGroup group = PropertyGroup.createOn(element, o);
        if (group != null) {
          PropertyGroup existing = (PropertyGroup)result.get(group);
          if (existing != null) {
            existing.copyAccessorsFrom(group);
          }
          else {
            result.put(group, group);
          }
        }
      }
    }
    for (Iterator<Group> iterator = result.keySet().iterator(); iterator.hasNext();) {
      PropertyGroup group = (PropertyGroup)iterator.next();
      if (!group.isComplete()) {
        iterator.remove();
      }
    }
    return result.values();
  }

  @Override
  public @NotNull ActionPresentation getPresentation() {
    return new ActionPresentationData(JavaStructureViewBundle.message("action.structureview.show.properties"), null, IconManager.getInstance().getPlatformIcon(PlatformIcons.Property));
  }

  @Override
  public @NotNull String getName() {
    return ID;
  }
}
