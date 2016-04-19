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

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.SoftHashMap;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Dmitry Batkovich
 */
class InspectionTreeCellRenderer extends ColoredTreeCellRenderer {
  private final static int MAX_LEVEL_TYPES = 5;

  private final Map<HighlightSeverity, String> myPluralizedSeverityNames = new SoftHashMap<>();
  private final Map<HighlightSeverity, String> myUnpluralizedSeverityNames = new SoftHashMap<>();
  private final FactoryMap<HighlightDisplayLevel, Integer> myItemCounter;
  private final InspectionResultsView myView;
  private final SeverityRegistrar myRegistrar;

  public InspectionTreeCellRenderer(InspectionResultsView view) {
    myRegistrar = SeverityRegistrar.getSeverityRegistrar(view.getProject());
    myItemCounter = new FactoryMap<HighlightDisplayLevel, Integer>() {
      @Nullable
      @Override
      protected Integer create(HighlightDisplayLevel key) {
        return 0;
      }

      @Override
      protected Map<HighlightDisplayLevel, Integer> createMap() {
        return new TreeMap<>(new Comparator<Object>() {

          @Override
          public int compare(Object o1, Object o2) {
            return -myRegistrar.compare(((HighlightDisplayLevel) o1).getSeverity(), ((HighlightDisplayLevel) o2).getSeverity());
          }
        });
      }
    };
    myView = view;
  }

  @Override
  public void customizeCellRenderer(JTree tree,
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

    myItemCounter.clear();
    node.visitProblemSeverities(myItemCounter);
    append("  ");
    final String customizedTailText = node.getCustomizedTailText();
    if (customizedTailText != null) {
      append(customizedTailText, SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
    else {
      if (myItemCounter.size() > MAX_LEVEL_TYPES) {
        append(InspectionsBundle.message("inspection.problem.descriptor.count",
                                         myItemCounter.values().stream().reduce(0, (i, j) -> i + j)) + " ",
               SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      else {
        for (Map.Entry<HighlightDisplayLevel, Integer> entry : myItemCounter.entrySet()) {
          final HighlightDisplayLevel level = entry.getKey();
          final Integer occur = entry.getValue();

          SimpleTextAttributes attrs = SimpleTextAttributes.GRAY_ATTRIBUTES;
          if (level == HighlightDisplayLevel.ERROR && !myView.getGlobalInspectionContext().getUIOptions().GROUP_BY_SEVERITY) {
            attrs = attrs.derive(-1, JBColor.red.brighter(), null, null);
          }
          append(occur + " " + getPresentableName(level, occur > 1) + " ", attrs);
        }
      }
    }

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

  private String getPresentableName(HighlightDisplayLevel level, boolean pluralize) {
    final HighlightSeverity severity = level.getSeverity();
    if (pluralize) {
      String name = myPluralizedSeverityNames.get(severity);
      if (name == null) {
        final String lowerCaseName = level.getName().toLowerCase(Locale.ENGLISH);
        name = myPluralizedSeverityNames
          .put(severity, myRegistrar.isDefaultSeverity(severity) ? StringUtil.pluralize(lowerCaseName) : lowerCaseName);
      }
      return name;
    } else {
      String name = myUnpluralizedSeverityNames.get(severity);
      if (name == null) {
        name = myUnpluralizedSeverityNames.put(severity, level.getName().toLowerCase(Locale.ENGLISH));
      }
      return name;
    }
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
