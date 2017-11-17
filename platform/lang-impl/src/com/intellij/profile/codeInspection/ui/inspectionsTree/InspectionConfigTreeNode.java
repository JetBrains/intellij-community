// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.profile.codeInspection.ui.inspectionsTree;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.Descriptor;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.profile.codeInspection.ui.ToolDescriptors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.stream.IntStream;

public abstract class InspectionConfigTreeNode extends DefaultMutableTreeNode {
  private final ClearableLazyValue<Boolean> myProperSetting = ClearableLazyValue.create(this::calculateIsProperSettings);

  public static class Group extends InspectionConfigTreeNode {
    public Group(@NotNull String label) {
      setUserObject(label);
    }

    @Override
    protected boolean calculateIsProperSettings() {
      return IntStream.range(0, getChildCount()).mapToObj(i -> (InspectionConfigTreeNode)getChildAt(i)).anyMatch(InspectionConfigTreeNode::isProperSetting);
    }
  }

  public static class Tool extends InspectionConfigTreeNode {
    @NotNull private final HighlightDisplayKey myKey;
    @NotNull private final SingleInspectionProfilePanel myPanel;

    public Tool(@NotNull HighlightDisplayKey key, @NotNull SingleInspectionProfilePanel panel) {
      myKey = key;
      myPanel = panel;
    }

    @Override
    public Object getUserObject() {
      return myPanel.getInitialToolDescriptors().get(myKey);
    }

    @Override
    protected boolean calculateIsProperSettings() {
      final Descriptor defaultDescriptor = getDescriptors().getDefaultDescriptor();
      return defaultDescriptor.getInspectionProfile().isProperSetting(defaultDescriptor.getToolWrapper().getShortName());
    }
  }

  public HighlightDisplayKey getKey() {
    return getDefaultDescriptor().getKey();
  }

  @Nullable
  public Descriptor getDefaultDescriptor() {
    final ToolDescriptors descriptors = getDescriptors();
    return descriptors == null ? null : descriptors.getDefaultDescriptor();
  }

  @Nullable
  public ToolDescriptors getDescriptors() {
    final Object userObject = getUserObject();
    return userObject instanceof String ? null : (ToolDescriptors)userObject;
  }

  @Nullable
  public String getGroupName() {
    return userObject instanceof String ? (String)userObject : null;
  }

  @Nullable
  public String getScopeName() {
    final ToolDescriptors descriptors = getDescriptors();
    return descriptors != null ? descriptors.getDefaultScopeToolState().getScopeName() : null;
  }

  public final boolean isProperSetting() {
    return myProperSetting.getValue();
  }

  public final void dropCache() {
    myProperSetting.drop();
  }

  protected abstract boolean calculateIsProperSettings();

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
}