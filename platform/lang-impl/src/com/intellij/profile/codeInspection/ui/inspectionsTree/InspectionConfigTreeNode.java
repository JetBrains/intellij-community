// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.profile.codeInspection.ui.inspectionsTree;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.Descriptor;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.util.Getter;
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

    @NotNull
    @Override
    public String getText() {
      return getGroupName();
    }

    @NotNull
    public String getGroupName() {
      return (String)getUserObject();
    }
  }

  public static class Tool extends InspectionConfigTreeNode {
    private final Getter<ToolDescriptors> myGetter;

    public Tool(Getter<ToolDescriptors> getter) {
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

    @NotNull
    @Override
    public String getText() {
      return getDefaultDescriptor().getText();
    }

    public HighlightDisplayKey getKey() {
      return getDefaultDescriptor().getKey();
    }

    @NotNull
    public Descriptor getDefaultDescriptor() {
      return getDescriptors().getDefaultDescriptor();
    }

    @NotNull
    public ToolDescriptors getDescriptors() {
      return (ToolDescriptors)getUserObject();
    }


    @Nullable
    public String getScopeName() {
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

  @NotNull
  public abstract String getText();

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