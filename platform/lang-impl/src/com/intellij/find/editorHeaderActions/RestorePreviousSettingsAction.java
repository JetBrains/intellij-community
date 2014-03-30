/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.event.KeyEvent;
import java.util.Collections;

/**
* Created by IntelliJ IDEA.
* User: zajac
* Date: 05.03.11
* Time: 10:40
* To change this template use File | Settings | File Templates.
*/
public class RestorePreviousSettingsAction extends EditorHeaderAction implements DumbAware {
  private final JTextComponent myTextField;
  private static final KeyboardShortcut SHORTCUT = new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), null);

  public RestorePreviousSettingsAction(EditorSearchComponent editorSearchComponent, JTextComponent textField) {
    super(editorSearchComponent);
    myTextField = textField;
    registerShortcutsForComponent(Collections.<Shortcut>singletonList(SHORTCUT),
                                  textField, this);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    getEditorSearchComponent().restoreFindModel();
  }

  @Override
  public void update(final AnActionEvent e) {
    e.getPresentation().setEnabled(myTextField.getText().isEmpty());
  }

  public static String getAd() {
    return "Use " + KeymapUtil.getShortcutText(SHORTCUT) + " to restore previous find/replace settings";
  }
}
