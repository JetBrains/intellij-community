/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dmitry Batkovich
 */
class InspectionTreeCellRenderer extends ColoredTreeCellRenderer {
  private final InspectionResultsView myView;
  private final InspectionTreeTailRenderer myTailRenderer;

  public InspectionTreeCellRenderer(InspectionResultsView view) {
    myTailRenderer = new InspectionTreeTailRenderer(view.getGlobalInspectionContext()) {
      @Override
      protected void appendText(String text, SimpleTextAttributes attributes) {
        append(text, attributes);
      }

      @Override
      protected void appendText(String text) {
        append(text);
      }
    };
    myView = view;
  }

  @Override
  public void customizeCellRenderer(@NotNull JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    InspectionTreeNode node = (InspectionTreeNode)value;

    append(node.toString(),
           patchMainTextAttrs(node, node.appearsBold()
                                    ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                                    : getMainForegroundAttributes(node)));
    myTailRenderer.appendTailText(node);
    setIcon(node.getIcon(expanded));
    // do not need reset model (for recalculation of prefered size) when digit number of problemCount is growth
    // or INVALID marker appears
    append(StringUtil.repeat(" ", 50));
  }

  private SimpleTextAttributes patchMainTextAttrs(InspectionTreeNode node, SimpleTextAttributes attributes) {
    if (node.isExcluded(myView.getExcludedManager())) {
      return attributes.derive(attributes.getStyle() | SimpleTextAttributes.STYLE_STRIKEOUT, null, null, null);
    }
    if (node instanceof ProblemDescriptionNode && ((ProblemDescriptionNode)node).isQuickFixAppliedFromView()) {
      return attributes.derive(-1, SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor(), null, null);
    }
    if (!node.isValid()) {
      return attributes.derive(-1, FileStatus.IGNORED.getColor(), null, null);
    }
    return attributes;
  }


  private static SimpleTextAttributes getMainForegroundAttributes(InspectionTreeNode node) {
    SimpleTextAttributes foreground = SimpleTextAttributes.REGULAR_ATTRIBUTES;
    if (node instanceof RefElementNode) {
      RefEntity refElement = ((RefElementNode)node).getElement();

      if (refElement instanceof RefElement) {
        refElement = ((RefElement)refElement).getContainingEntry();
        if (((RefElement)refElement).isEntry() && ((RefElement)refElement).isPermanentEntry()) {
          foreground = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.blue);
        }
      }
    }
    final FileStatus nodeStatus = node.getNodeStatus();
    if (nodeStatus != FileStatus.NOT_CHANGED) {
      foreground =
        new SimpleTextAttributes(foreground.getBgColor(), nodeStatus.getColor(), foreground.getWaveColor(), foreground.getStyle());
    }
    return foreground;
  }
}
