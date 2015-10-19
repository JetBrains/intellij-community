package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchSession;
import com.intellij.find.FindModel;
import com.intellij.find.FindUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
* Created by IntelliJ IDEA.
* User: zajac
* Date: 05.03.11
* Time: 10:57
* To change this template use File | Settings | File Templates.
*/
public class SwitchToFind extends AnAction implements DumbAware {
  public SwitchToFind(@NotNull JComponent shortcutHolder) {
    AnAction findAction = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND);
    if (findAction != null) {
      registerCustomShortcutSet(findAction.getShortcutSet(), shortcutHolder);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    if (KeymapUtil.isEmacsKeymap()) {
      // Emacs users are accustomed to the editor that executes 'find next' on subsequent pressing of shortcut that
      // activates 'incremental search'. Hence, we do the similar hack here for them.
      ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_NEXT).update(e);
    }
    else {
      EditorSearchSession search = e.getData(EditorSearchSession.SESSION_KEY);
      e.getPresentation().setEnabledAndVisible(search != null);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (KeymapUtil.isEmacsKeymap()) {
      // Emacs users are accustomed to the editor that executes 'find next' on subsequent pressing of shortcut that
      // activates 'incremental search'. Hence, we do the similar hack here for them.
      ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_NEXT).actionPerformed(e);
      return;
    }

    EditorSearchSession search = e.getRequiredData(EditorSearchSession.SESSION_KEY);
    final FindModel findModel = search.getFindModel();
    FindUtil.configureFindModel(false, e.getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE), findModel, false);
    search.getComponent().getSearchTextComponent().selectAll();
  }
}
