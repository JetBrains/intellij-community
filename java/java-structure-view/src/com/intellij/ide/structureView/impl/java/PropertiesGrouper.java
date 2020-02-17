// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

public class PropertiesGrouper implements Grouper{
  @NonNls public static final String ID = "SHOW_PROPERTIES";

  @Override
  @NotNull
  public Collection<Group> group(@NotNull AbstractTreeNode<?> parent, @NotNull Collection<TreeElement> children) {
    if (parent.getValue() instanceof PropertyGroup) return Collections.emptyList();
    Map<Group,Group> result = new THashMap<>();
    for (TreeElement o : children) {
      if (o instanceof JavaClassTreeElementBase) {
        PsiElement element = ((JavaClassTreeElementBase)o).getElement();
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
  @NotNull
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(JavaStructureViewBundle.message("action.structureview.show.properties"), null, PlatformIcons.PROPERTY_ICON);
  }

  @Override
  @NotNull
  public String getName() {
    return ID;
  }
}
