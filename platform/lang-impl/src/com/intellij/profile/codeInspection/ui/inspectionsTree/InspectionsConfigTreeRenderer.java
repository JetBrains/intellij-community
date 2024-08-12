// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.profile.codeInspection.ui.inspectionsTree;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.Descriptor;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.internal.inspector.PropertyBean;
import com.intellij.internal.inspector.UiInspectorTreeRendererContextProvider;
import com.intellij.internal.inspector.UiInspectorUtil;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.ui.PlatformColors;
import com.intellij.util.ui.UIUtil;
import org.jdesktop.swingx.renderer.DefaultTreeRenderer;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Internal
public abstract class InspectionsConfigTreeRenderer extends DefaultTreeRenderer implements UiInspectorTreeRendererContextProvider {
  protected abstract String getFilter();

  @Override
  public Component getTreeCellRendererComponent(JTree tree,
                                                Object value,
                                                boolean selected,
                                                boolean expanded,
                                                boolean leaf,
                                                int row,
                                                boolean hasFocus) {
    final SimpleColoredComponent component = new SimpleColoredComponent();
    if (!(value instanceof InspectionConfigTreeNode node)) return component;

    boolean reallyHasFocus = ((TreeTableTree)tree).getTreeTable().hasFocus();
    Color background = UIUtil.getTreeBackground(selected, reallyHasFocus);
    UIUtil.changeBackGround(component, background);
    Color foreground =
      selected ? UIUtil.getTreeSelectionForeground(reallyHasFocus) : node.isProperSetting() ? PlatformColors.BLUE : UIUtil.getTreeForeground();

    int style = SimpleTextAttributes.STYLE_PLAIN;
    String hint = null;
    if (node instanceof InspectionConfigTreeNode.Group) {
      style = SimpleTextAttributes.STYLE_BOLD;
    }
    else {
      InspectionConfigTreeNode.Tool toolNode = (InspectionConfigTreeNode.Tool)node;
      hint = getHint(toolNode.getDefaultDescriptor());
    }

    SearchUtil.appendFragments(getFilter(), node.getText(), style, foreground, background, component);
    if (hint != null) {
      component.append(" " + hint, selected ? new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, foreground) : SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
    component.setForeground(foreground);
    return component;
  }

  private static @Nullable @NlsContexts.Label String getHint(final Descriptor descriptor) {
    final InspectionToolWrapper toolWrapper = descriptor.getToolWrapper();

    if (toolWrapper instanceof LocalInspectionToolWrapper ||
        toolWrapper instanceof GlobalInspectionToolWrapper && !((GlobalInspectionToolWrapper)toolWrapper).worksInBatchModeOnly()) {
      return null;
    }
    return InspectionsBundle.message("inspection.tool.availability.in.tree.node1");
  }

  @Override
  public @NotNull List<PropertyBean> getUiInspectorContext(@NotNull JTree tree, @Nullable Object value, int row) {
    if (value instanceof InspectionConfigTreeNode.Tool toolNode) {
      List<PropertyBean> result = new ArrayList<>();
      result.add(new PropertyBean("Inspection Key", toolNode.getKey().getID(), true));
      result.add(new PropertyBean("Inspection tool Class",
                                  UiInspectorUtil.getClassPresentation(toolNode.getDefaultDescriptor().getToolWrapper().getTool()), true));
      return result;
    }
    return Collections.emptyList();
  }
}