/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightColors;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author max
 */
public class FileLevelIntentionComponent extends JPanel {
  private static final Icon ourIntentionIcon = IconLoader.getIcon("/actions/intentionBulb.png");
  private static final Icon ourQuickFixIcon = IconLoader.getIcon("/actions/quickfixBulb.png");

  private final Project myProject;
  private final Editor myEditor;

  public FileLevelIntentionComponent(final String description,
                                     final HighlightSeverity severity,
                                     List<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>> intentions,
                                     final Project project, final PsiFile psiFile, final Editor editor) {
    super(new BorderLayout());
    myEditor = editor;
    myProject = project;

    final ShowIntentionsPass.IntentionsInfo info = new ShowIntentionsPass.IntentionsInfo();

    if (intentions != null) {
      for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> intention : intentions) {
        info.intentionsToShow.add(intention.getFirst());
      }
    }

    JLabel content =
      new JLabel(description, SeverityRegistrar.getInstance(project).compare(severity, HighlightSeverity.ERROR) >= 0 ? ourQuickFixIcon : ourIntentionIcon,
                 SwingConstants.LEADING);
    add(content, BorderLayout.WEST);
    content.setBackground(null);
    setBackground(getColor(severity));

    content.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        Point location = SwingUtilities.convertPoint(FileLevelIntentionComponent.this,
                                                     new Point(0, 0),
                                                     myEditor.getComponent().getRootPane().getLayeredPane());
        IntentionHintComponent.showIntentionHint(myProject, psiFile, myEditor, info, true, location);
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
