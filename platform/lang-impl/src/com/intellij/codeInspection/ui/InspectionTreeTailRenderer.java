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
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TObjectIntHashMap;

import java.awt.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public abstract class InspectionTreeTailRenderer {
  private final static int MAX_LEVEL_TYPES = 5;

  private final static JBColor TREE_RED = new JBColor(new Color(184, 66, 55), new Color(204, 102, 102));
  private final static JBColor TREE_GRAY = new JBColor(Gray._153, Gray._117);

  private final Map<HighlightSeverity, String> myPluralizedSeverityNames = ContainerUtil.createSoftMap();
  private final Map<HighlightSeverity, String> myUnpluralizedSeverityNames = ContainerUtil.createSoftMap();

  private final TObjectIntHashMap<HighlightDisplayLevel> myItemCounter = new TObjectIntHashMap<>();
  private final SeverityRegistrar myRegistrar;
  private final GlobalInspectionContextImpl myContext;

  public InspectionTreeTailRenderer(GlobalInspectionContextImpl context) {
    myRegistrar = SeverityRegistrar.getSeverityRegistrar(context.getProject());
    myContext = context;
  }

  public void appendTailText(InspectionTreeNode node) {
    appendText("  ");
    final String customizedTailText = node.getTailText();
    if (customizedTailText != null) {
      appendText("  ");
      appendText(customizedTailText, SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
    else {
      myItemCounter.clear();
      node.visitProblemSeverities(myItemCounter);
      if (myItemCounter.size() > MAX_LEVEL_TYPES) {
        appendText(InspectionsBundle.message("inspection.problem.descriptor.count", sum(myItemCounter.getValues()), SimpleTextAttributes.GRAYED_ATTRIBUTES));
      }
      else {
        Object[] levels = myItemCounter.keys();
        Arrays.sort(levels, Comparator.comparing(l -> ((HighlightDisplayLevel) l).getSeverity()).reversed());
        for (Object o : levels) {
          HighlightDisplayLevel level = (HighlightDisplayLevel) o;

          int occur = myItemCounter.get(level);

          SimpleTextAttributes attrs = SimpleTextAttributes.GRAY_ATTRIBUTES;
          attrs = attrs.derive(-1, level == HighlightDisplayLevel.ERROR && !myContext.getUIOptions().GROUP_BY_SEVERITY
                                   ? TREE_RED
                                   : TREE_GRAY, null, null);
          appendText(occur + " " + getPresentableName(level, occur > 1) + " ", attrs);
        }
      }
    }
  }

  protected abstract void appendText(String text, SimpleTextAttributes attributes);

  protected abstract void appendText(String text);

  private String getPresentableName(HighlightDisplayLevel level, boolean pluralize) {
    final HighlightSeverity severity = level.getSeverity();
    if (pluralize) {
      String name = myPluralizedSeverityNames.get(severity);
      if (name == null) {
        final String lowerCaseName = level.getName().toLowerCase(Locale.ENGLISH);
        name = myRegistrar.isDefaultSeverity(severity) ? StringUtil.pluralize(lowerCaseName) : lowerCaseName;
        myPluralizedSeverityNames.put(severity, name);
      }
      return name;
    }
    else {
      String name = myUnpluralizedSeverityNames.get(severity);
      if (name == null) {
        name = level.getName().toLowerCase(Locale.ENGLISH);
        myUnpluralizedSeverityNames.put(severity, name);
      }
      return name;
    }
  }

  private static int sum(int[] numbers) {
    int result = 0;
    for (int number : numbers) {
      result += number;
    }
    return result;
  }
}
