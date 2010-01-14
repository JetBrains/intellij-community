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

package com.intellij.openapi.wm.impl.status;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.HectorComponent;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.UIBundle;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class TogglePopupHintsPanel extends JPanel implements StatusBarPatch{
  private static final Icon INSPECTIONS_ICON = IconLoader.getIcon("/objectBrowser/showGlobalInspections.png");
  private static final Icon INSPECTIONS_OFF_ICON = IconLoader.getIcon("/general/inspectionsOff.png");
  private static final Icon EMPTY_ICON = new EmptyIcon(INSPECTIONS_ICON.getIconWidth(), INSPECTIONS_ICON.getIconHeight());

  private final JLabel myHectorLabel = new JLabel(EMPTY_ICON);

  public TogglePopupHintsPanel(final StatusBar statusBar) {
    super(new GridBagLayout());
    myHectorLabel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        Point point = new Point(0, 0);
        final PsiFile file = getCurrentFile();
        if (file != null) {
          if (!DaemonCodeAnalyzer.getInstance(file.getProject()).isHighlightingAvailable(file)) return;
          final HectorComponent component = new HectorComponent(file);
          final Dimension dimension = component.getPreferredSize();
          point = new Point(point.x - dimension.width, point.y - dimension.height);
          component.showComponent(new RelativePoint(TogglePopupHintsPanel.this, point));
        }
      }
    });
    myHectorLabel.setIconTextGap(0);

    add(myHectorLabel, new GridBagConstraints(0, 0, 1, 1, 0, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));

    StatusBarTooltipper.install(this, myHectorLabel, statusBar);
  }



  public JComponent getComponent() {
    return this;
  }

  public String updateStatusBar(final Editor selected, final JComponent componentSelected) {
    updateStatus();
    String text = componentSelected == null ? null : componentSelected.getToolTipText();
    setCursor(Cursor.getPredefinedCursor(text == null ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
    return text;
  }

  public void clear() {
    myHectorLabel.setIcon(EMPTY_ICON);
    myHectorLabel.setToolTipText(null);
  }

  public void updateStatus() {
    updateStatus(getCurrentFile());
  }

  private void updateStatus(PsiFile file) {
    if (isStateChangeable(file)) {
      String tooltip;
      if (HighlightLevelUtil.shouldInspect(file)) {
        myHectorLabel.setIcon(INSPECTIONS_ICON);
        tooltip =  "Current inspection profile: " + InspectionProjectProfileManager.getInstance(file.getProject()).getInspectionProfile().getName() + ". ";
      }
      else {
        myHectorLabel.setIcon(INSPECTIONS_OFF_ICON);
        tooltip = "Inspections are off. ";
      }
      myHectorLabel.setToolTipText(tooltip + UIBundle.message("popup.hints.panel.click.to.configure.highlighting.tooltip.text"));
    }
    else {
      myHectorLabel.setIcon(EMPTY_ICON);
      myHectorLabel.setToolTipText(null);
    }
  }

  private static boolean isStateChangeable(PsiFile file) {
    return file != null && DaemonCodeAnalyzer.getInstance(file.getProject()).isHighlightingAvailable(file);
  }

  @Nullable
  private PsiFile getCurrentFile() {
    final Project project = getCurrentProject();
    if (project == null) {
      return null;
    }

    final VirtualFile virtualFile = ((FileEditorManagerEx)FileEditorManager.getInstance(project)).getCurrentFile();
    if (virtualFile != null && virtualFile.isValid()){
      return PsiManager.getInstance(project).findFile(virtualFile);
    }
    return null;
  }

  @Nullable
  private Project getCurrentProject() {
    return PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this));
  }

  public Point getToolTipLocation(MouseEvent event) {
    return new Point(0, -20);
  }
}
