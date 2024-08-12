// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui.inspectionsTree;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.Descriptor;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.profile.codeInspection.ui.ToolDescriptors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public abstract class InspectionConfigTreeNode extends DefaultMutableTreeNode {
  private final ClearableLazyValue<Boolean> myProperSetting = ClearableLazyValue.create(this::calculateIsProperSettings);

  public static final class Group extends InspectionConfigTreeNode {
    public Group(@NotNull String label) {
      setUserObject(label);
    }

    @Override
    protected boolean calculateIsProperSettings() {
      return IntStream.range(0, getChildCount()).mapToObj(i -> (InspectionConfigTreeNode)getChildAt(i)).anyMatch(InspectionConfigTreeNode::isProperSetting);
    }

    @Override
    public @NotNull String getText() {
      return getGroupName();
    }

    public @NotNull String getGroupName() {
      return (String)getUserObject();
    }
  }

  public static class Tool extends InspectionConfigTreeNode {
    private final Supplier<ToolDescriptors> myGetter;

    public Tool(Supplier<ToolDescriptors> getter) {
      myGetter = getter;
    }

    @Override
    public Object getUserObject() {
      return myGetter.get();
    }

    @Override
    protected boolean calculateIsProperSettings() {
      final Descriptor defaultDescriptor = getDescriptors().getDefaultDescriptor();
      return defaultDescriptor.getInspectionProfile().isProperSetting(defaultDescriptor.getToolWrapper().getShortName());
    }

    @Override
    public @NotNull String getText() {
      return getDefaultDescriptor().getText();
    }

    public HighlightDisplayKey getKey() {
      return getDefaultDescriptor().getKey();
    }

    public @NotNull Descriptor getDefaultDescriptor() {
      return getDescriptors().getDefaultDescriptor();
    }

    public @NotNull ToolDescriptors getDescriptors() {
      return (ToolDescriptors)getUserObject();
    }


    public @Nullable String getScopeName() {
      return getDescriptors().getDefaultScopeToolState().getScopeName();
    }
  }


  public final boolean isProperSetting() {
    return myProperSetting.getValue();
  }

  public final void dropCache() {
    myProperSetting.drop();
  }

  protected abstract boolean calculateIsProperSettings();

  public abstract @NotNull String getText();

  @Override
  public String toString() {
    final Object userObject = getUserObject();
    if (userObject instanceof ToolDescriptors) {
      return ((ToolDescriptors)userObject).getDefaultDescriptor().getText();
    }
    if (userObject instanceof Descriptor) {
      return ((Descriptor)userObject).getText();
    }
    return super.toString();
  }

  public static void updateUpHierarchy(@NotNull InspectionConfigTreeNode node) {
    updateUpHierarchy(Collections.singletonList(node));
  }

  public static void updateUpHierarchy(Collection<? extends InspectionConfigTreeNode> nodes) {
    Deque<InspectionConfigTreeNode> q = new ArrayDeque<>(nodes.size());
    Set<InspectionConfigTreeNode> alreadyUpdated = new HashSet<>();
    for (InspectionConfigTreeNode node : nodes) {
      q.addLast(node);
    }
    while (true) {
      InspectionConfigTreeNode inspectionConfigTreeNode = q.pollFirst();
      if (inspectionConfigTreeNode == null) {
        break;
      }
      if (!alreadyUpdated.add(inspectionConfigTreeNode)) {
        continue;
      }
      inspectionConfigTreeNode.dropCache();
      TreeNode parent = inspectionConfigTreeNode.getParent();
      if (parent != null && parent.getParent() != null) {
        q.addLast((InspectionConfigTreeNode)parent);
      }
    }
  }
}