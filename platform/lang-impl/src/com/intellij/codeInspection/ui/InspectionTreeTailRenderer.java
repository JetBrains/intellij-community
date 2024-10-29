// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.openapi.util.text.Strings;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nls;

import java.awt.*;
import java.util.Arrays;

/**
 * @author Dmitry Batkovich
 */
public abstract class InspectionTreeTailRenderer<E extends Exception> {
  private static final int MAX_LEVEL_TYPES = 5;

  private static final JBColor TREE_RED = new JBColor(new Color(184, 66, 55), new Color(204, 102, 102));
  private static final JBColor TREE_GRAY = new JBColor(Gray._153, Gray._117);

  private final GlobalInspectionContextImpl myContext;

  public InspectionTreeTailRenderer(GlobalInspectionContextImpl context) {
    myContext = context;
  }

  public void appendTailText(InspectionTreeNode node) throws E {
    if (myContext.isViewClosed()) {
      return;
    }
    final String customizedTailText = node.getTailText();
    if (customizedTailText != null) {
      if (!customizedTailText.isEmpty()) {
        appendText("    ");
        appendText(customizedTailText, SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    }
    else {
      if (ExperimentalUI.isNewUI() && node instanceof InspectionRootNode) {
        String profile = myContext.getCurrentProfile().getDisplayName();
        if (!Strings.isEmpty(profile)) {
          appendText(" ");
          appendText(InspectionsBundle.message("inspection.results.profile", profile), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
      }

      appendText("  ");
      LevelAndCount[] problemLevels = node.getProblemLevels();
      if (problemLevels.length > MAX_LEVEL_TYPES) {
        int sum = Arrays.stream(problemLevels).mapToInt(LevelAndCount::getCount).sum();
        appendText(InspectionsBundle.message("inspection.problem.descriptor.count", sum, SimpleTextAttributes.GRAYED_ATTRIBUTES));
      }
      else {
        for (LevelAndCount levelAndCount : problemLevels) {
          SimpleTextAttributes attrs = SimpleTextAttributes.GRAY_ATTRIBUTES;
          attrs = attrs.derive(-1, levelAndCount.getLevel() == HighlightDisplayLevel.ERROR && !myContext.getUIOptions().GROUP_BY_SEVERITY
                                   ? TREE_RED
                                   : TREE_GRAY, null, null);
          appendText(levelAndCount.getLevel().getSeverity().getCountMessage(levelAndCount.getCount()) + " ", attrs);
        }
      }
    }
  }

  protected abstract void appendText(@Nls String text, SimpleTextAttributes attributes) throws E;

  protected abstract void appendText(@Nls String text) throws E;
}
