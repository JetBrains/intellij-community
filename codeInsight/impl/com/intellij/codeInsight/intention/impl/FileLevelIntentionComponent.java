/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.LightColors;
import com.intellij.psi.PsiFile;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * @author max
 */
public class FileLevelIntentionComponent extends JPanel {
  private static final Icon ourIntentionIcon = IconLoader.getIcon("/actions/intentionBulb.png");
  private static final Icon ourQuickFixIcon = IconLoader.getIcon("/actions/quickfixBulb.png");

  private ArrayList<HighlightInfo.IntentionActionDescriptor> myIntentions;
  private Project myProject;
  private Editor myEditor;

  public FileLevelIntentionComponent(final String description,
                                     final HighlightSeverity severity,
                                     List<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>> intentions,
                                     final Project project, final PsiFile psiFile, final Editor editor) {
    super(new BorderLayout());
    myEditor = editor;
    myProject = project;

    myIntentions = new ArrayList<HighlightInfo.IntentionActionDescriptor>();

    if (intentions != null) {
      for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> intention : intentions) {
        myIntentions.add(intention.getFirst());
      }
    }

    JLabel content =
      new JLabel(description, SeverityRegistrar.getInstance(project).compare(severity, HighlightSeverity.ERROR) >= 0 ? ourQuickFixIcon : ourIntentionIcon, JLabel.LEADING);
    add(content, BorderLayout.WEST);
    content.setBackground(null);
    setBackground(getColor(severity));

    content.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        IntentionHintComponent.showIntentionHint(myProject, psiFile, myEditor,
                                                 Collections.<HighlightInfo.IntentionActionDescriptor>emptyList(),
                                                 myIntentions, Collections.<HighlightInfo.IntentionActionDescriptor>emptyList(), true);
      }
    });
  }

  private  Color getColor(HighlightSeverity severity) {
    if (SeverityRegistrar.getInstance(myProject).compare(severity, HighlightSeverity.ERROR) >= 0) {
      return LightColors.RED;
    }

    if (SeverityRegistrar.getInstance(myProject).compare(severity, HighlightSeverity.WARNING) >= 0) {
      return LightColors.YELLOW;
    }

    return Color.white;
  }
}
