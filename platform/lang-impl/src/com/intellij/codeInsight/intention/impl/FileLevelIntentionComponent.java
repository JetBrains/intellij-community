/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.LightColors;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author max
 */
public class FileLevelIntentionComponent extends EditorNotificationPanel {
  private static final Icon ourIntentionIcon = IconLoader.getIcon("/actions/intentionBulb.png");
  private static final Icon ourQuickFixIcon = IconLoader.getIcon("/actions/quickfixBulb.png");

  private final Project myProject;

  public FileLevelIntentionComponent(final String description,
                                     final HighlightSeverity severity,
                                     final List<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>> intentions,
                                     final Project project, final PsiFile psiFile, final Editor editor) {
    myProject = project;

    final ShowIntentionsPass.IntentionsInfo info = new ShowIntentionsPass.IntentionsInfo();

    if (intentions != null) {
      for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> intention : intentions) {
        final HighlightInfo.IntentionActionDescriptor descriptor = intention.getFirst();
        info.intentionsToShow.add(descriptor);
        createActionLabel(descriptor.getAction().getText(), new Runnable() {
          public void run() {
            descriptor.getAction().invoke(project, editor, psiFile);
          }
        });
      }
    }

    myLabel.setText(description);
    myLabel.setIcon(SeverityRegistrar.getInstance(project).compare(severity, HighlightSeverity.ERROR) >= 0 ? ourQuickFixIcon : ourIntentionIcon);
    setBackground(getColor(severity));

    myLabel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        IntentionListStep step = new IntentionListStep(null, info, editor, psiFile, project);
        if (intentions != null && !intentions.isEmpty()) {
          HighlightInfo.IntentionActionDescriptor descriptor = intentions.get(0).getFirst();
          IntentionActionWithTextCaching actionWithTextCaching = step.wrapAction(descriptor, psiFile, psiFile, editor);
          step = step.getSubStep(actionWithTextCaching, null);
        }
        JBPopupFactory.getInstance().createListPopup(step).showUnderneathOf(myLabel);
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
