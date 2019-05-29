// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.UISettings;
import com.intellij.internal.statistic.service.fus.collectors.UIEventId;
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.psi.PsiFile;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class DaemonEditorPopup extends PopupHandler {
  private final PsiFile myPsiFile;

  DaemonEditorPopup(final PsiFile psiFile) {
    myPsiFile = psiFile;
  }

  @Override
  public void invokePopup(final Component comp, final int x, final int y) {
    if (ApplicationManager.getApplication() == null) return;
    ActionManager actionManager = ActionManager.getInstance();
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    Shortcut[] shortcuts = keymap.getShortcuts("GotoNextError");
    String shortcutText = shortcuts.length > 0 ? " (" + KeymapUtil.getShortcutText(shortcuts[0]) + ")" : "";
    DefaultActionGroup gotoGroup = new DefaultActionGroup("'Next Error' Action" + shortcutText + " Goes Through", true);
    gotoGroup.add(new ToggleAction(EditorBundle.message("errors.panel.go.to.errors.first.radio")) {
                    @Override
                    public boolean isSelected(@NotNull AnActionEvent e) {
                      return DaemonCodeAnalyzerSettings.getInstance().isNextErrorActionGoesToErrorsFirst();
                    }

                    @Override
                    public void setSelected(@NotNull AnActionEvent e, boolean state) {
                      DaemonCodeAnalyzerSettings.getInstance().setNextErrorActionGoesToErrorsFirst(state);
                    }
                  }
    );
    gotoGroup.add(new ToggleAction(EditorBundle.message("errors.panel.go.to.next.error.warning.radio")) {
                    @Override
                    public boolean isSelected(@NotNull AnActionEvent e) {
                      return !DaemonCodeAnalyzerSettings.getInstance().isNextErrorActionGoesToErrorsFirst();
                    }

                    @Override
                    public void setSelected(@NotNull AnActionEvent e, boolean state) {
                      DaemonCodeAnalyzerSettings.getInstance().setNextErrorActionGoesToErrorsFirst(!state);
                    }
                  }
    );
    actionGroup.add(gotoGroup);
    actionGroup.addSeparator();
    actionGroup.add(new AnAction(EditorBundle.message("customize.highlighting.level.menu.item")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        PsiFile psiFile = myPsiFile;
        if (psiFile == null) return;
        final HectorComponent component = new HectorComponent(ObjectUtils.assertNotNull(psiFile));
        final Dimension dimension = component.getPreferredSize();
        Point point = new Point(x, y);
        component.showComponent(new RelativePoint(comp, new Point(point.x - dimension.width, point.y)));
      }
    });
    actionGroup.addSeparator();
    actionGroup.add(new ToggleAction(IdeBundle.message("checkbox.show.editor.preview.popup")) {
      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        return UISettings.getInstance().getShowEditorToolTip();
      }

      @Override
      public void setSelected(@NotNull AnActionEvent e, boolean state) {
        UISettings.getInstance().setShowEditorToolTip(state);
        UISettings.getInstance().fireUISettingsChanged();
      }
    });
    ActionPopupMenu editorPopup = actionManager.createActionPopupMenu(ActionPlaces.RIGHT_EDITOR_GUTTER_POPUP, actionGroup);
    PsiFile file = myPsiFile;
    if (file != null && DaemonCodeAnalyzer.getInstance(myPsiFile.getProject()).isHighlightingAvailable(file)) {
      UIEventLogger.logUIEvent(UIEventId.DaemonEditorPopupInvoked);
      editorPopup.getComponent().show(comp, x, y);
    }
  }
}