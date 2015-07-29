/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

/**
* Created by IntelliJ IDEA.
* User: zajac
* Date: 05.03.11
* Time: 10:40
* To change this template use File | Settings | File Templates.
*/
public class PrevOccurrenceAction extends EditorHeaderAction implements DumbAware {

  public PrevOccurrenceAction(EditorSearchComponent editorSearchComponent, JComponent shortcutHolder) {
    super(editorSearchComponent);

    copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_OCCURENCE));

    ArrayList<Shortcut> shortcuts = new ArrayList<Shortcut>();
    ContainerUtil.addAll(shortcuts, ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_PREVIOUS).getShortcutSet().getShortcuts());
    if (!editorSearchComponent.getFindModel().isMultiline()) {
      ContainerUtil.addAll(shortcuts,
                           ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP).getShortcutSet().getShortcuts());

      shortcuts.add(new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), null));
    }
    registerShortcutsForComponent(shortcuts, shortcutHolder);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    myEditorSearchComponent.searchBackward();
  }

  @Override
  public void update(final AnActionEvent e) {
    e.getPresentation().setEnabled(myEditorSearchComponent.hasMatches());
  }
}
