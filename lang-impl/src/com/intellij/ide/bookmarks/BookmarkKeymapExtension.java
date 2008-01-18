package com.intellij.ide.bookmarks;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.keymap.KeymapExtension;
import com.intellij.openapi.keymap.KeymapGroup;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;

/**
 * @author yole
 */
public class BookmarkKeymapExtension implements KeymapExtension {
  public KeymapGroup createGroup(final Condition<AnAction> filtered, final Project project) {
    return ActionsTreeUtil.createGroup((ActionGroup)ActionManager.getInstance().getActionOrStub(IdeActions.GROUP_BOOKMARKS), true, filtered);
  }
}