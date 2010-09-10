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
package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.HintHint;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author peter
 */
public class InspectionDescriptionLinkHandler extends TooltipLinkHandler {
  public void handleLink(@NotNull final String descriptionSuffix, @NotNull final Editor editor, @NotNull final JEditorPane hintComponent) {
    showDescription(descriptionSuffix, editor, hintComponent);
  }

  @Nullable
  public String getDescription(final String shortName, Editor editor) {
    final Project project = editor.getProject();
    assert project != null;
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    assert file != null;
    final InspectionProfileEntry tool =
      ((InspectionProfile)InspectionProfileManager.getInstance().getRootProfile()).getInspectionTool(shortName, file);
    if (tool == null) return null;

    String description;
    description = tool.loadDescription();
    if (description == null) {
      description = InspectionsBundle.message("inspection.tool.description.under.construction.text");
    }
    return description;
  }

  private void showDescription(final String shortName, final Editor editor, final JEditorPane tooltip) {
    final String description = getDescription(shortName, editor);
    if (description == null) return;
    final JEditorPane pane = LineTooltipRenderer.initPane(description, new HintHint(tooltip, new Point(0, 0)), editor.getComponent().getRootPane().getLayeredPane());
    pane.select(0, 0);
    pane.setPreferredSize(new Dimension(3 * tooltip.getPreferredSize().width /2, 200));
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(pane);
    scrollPane.setBorder(null);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    final JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(scrollPane, scrollPane).createPopup();
    pane.addMouseListener(new MouseAdapter(){
      public void mousePressed(final MouseEvent e) {
        final Component contentComponent = editor.getContentComponent();
        MouseEvent newMouseEvent = SwingUtilities.convertMouseEvent(e.getComponent(), e, contentComponent);
        popup.cancel();
        contentComponent.dispatchEvent(newMouseEvent);
      }
    });
    popup.showUnderneathOf(tooltip);
  }

}
