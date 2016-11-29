/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.help.impl;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.THashSet;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
public class KeymapGenerator implements ApplicationStarter {
  @Override
  public String getCommandName() {
    return "keymap";
  }

  @Override
  public void premain(String[] args) {

  }

  @Override
  public void main(String[] args) {
    ActionManager actionManager = ActionManager.getInstance();
    StringBuilder xml = new StringBuilder();
    xml.append("<Keymaps>\n");

    for (Keymap keymap : KeymapManagerEx.getInstanceEx().getAllKeymaps()) {

      xml.append("  <Keymap name=\"").append(keymap.getPresentableName()).append("\">\n");
      for (String id : keymap.getActionIds()) {
        String shortcuts = KeymapUtil.getShortcutsText(keymap.getShortcuts(id));
        if (!StringUtil.isEmpty(shortcuts)) {
          AnAction action = actionManager.getAction(id);
          xml.append("    <Action id=\"").append(id).append("\">\n");
          Set<String> addedShortcuts = new THashSet<>();
          for (Shortcut shortcut : keymap.getShortcuts(id)) {
            // Different shortcuts may have equal display strings (e.g. shift+minus and shift+subtract)
            // We don't want them do be duplicated for users
            String shortcutText = KeymapUtil.getShortcutText(shortcut);
            if (addedShortcuts.add(shortcutText)) {
              xml.append("      <Shortcut>").append(shortcutText).append("</Shortcut>\n");
            }
          }
          if (action != null) {
            String text = action.getTemplatePresentation().getText();
            if (text != null) {
              xml.append("      <Text>").append(StringUtil.escapeXml(text)).append("</Text>\n");
            }
          }
          xml.append("    </Action>\n");
        }
      }
      xml.append("  </Keymap>\n");
    }
    xml.append("</Keymaps>");

    final String path = args.length == 2 ? args[1] : PathManager.getHomePath() + File.separator + "AllKeymaps.xml";

    File out = new File(path);
    try {
      FileUtil.writeToFile(out, xml.toString());
      System.out.println("Saved to: " + out.getAbsolutePath());
    }
    catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
    }
    System.exit(0);
  }
}
