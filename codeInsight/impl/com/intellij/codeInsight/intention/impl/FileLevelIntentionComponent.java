/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.LightColors;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class FileLevelIntentionComponent extends JPanel {
  private static final Icon ourIntentionIcon = IconLoader.getIcon("/actions/intentionBulb.png");
  private static final Icon ourQuickFixIcon = IconLoader.getIcon("/actions/quickfixBulb.png");

  private String myDescription;
  private HighlightSeverity mySeverity;
  private List<Pair<Pair<IntentionAction,String>,List<IntentionAction>>> myIntentions;
  private Project myProject;
  private Editor myEditor;

  public FileLevelIntentionComponent(final String description,
                                     final HighlightSeverity severity,
                                     List<Pair<Pair<Pair<IntentionAction, String>, List<IntentionAction>>, TextRange>> intentions,
                                     final Project project,
                                     final Editor editor) {
    super(new BorderLayout());
    myEditor = editor;
    myProject = project;

    myDescription = description;
    mySeverity = severity;
    myIntentions = new ArrayList<Pair<Pair<IntentionAction, String>, List<IntentionAction>>>();

    if (intentions != null) {
      for (Pair<Pair<Pair<IntentionAction, String>, List<IntentionAction>>, TextRange> intention : intentions) {
        myIntentions.add(intention.getFirst());
      }
    }

    JLabel content =
      new JLabel(description, severity.compareTo(HighlightSeverity.ERROR) >= 0 ? ourQuickFixIcon : ourIntentionIcon, JLabel.LEADING);
    add(content, BorderLayout.WEST);
    content.setBackground(null);
    setBackground(getColor(severity));

    content.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        IntentionHintComponent.showIntentionHint(myProject, myEditor,
                                                 new ArrayList<Pair<Pair<IntentionAction, String>, List<IntentionAction>>>(),
                                                 myIntentions,
                                                 true);
      }
    });
  }

  private static Color getColor(HighlightSeverity severity) {
    if (severity.compareTo(HighlightSeverity.ERROR) >= 0) {
      return LightColors.RED;
    }

    if (severity.compareTo(HighlightSeverity.WARNING) >= 0) {
      return LightColors.YELLOW;
    }

    return Color.white;
  }
}
