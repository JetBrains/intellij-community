// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
final class KeymapGenerator implements ApplicationStarter {
  @Override
  public String getCommandName() {
    return "keymap";
  }

  @Override
  public void main(@NotNull List<String> args) {
    ActionManager actionManager = ActionManager.getInstance();
    StringBuilder xml = new StringBuilder();
    xml.append("<Keymaps>\n");

    for (Keymap keymap : KeymapManagerEx.getInstanceEx().getAllKeymaps()) {

      xml.append("  <Keymap name=\"").append(keymap.getPresentableName()).append("\">\n");
      for (String id : keymap.getActionIdList()) {
        String shortcuts = KeymapUtil.getShortcutsText(keymap.getShortcuts(id));
        if (!StringUtil.isEmpty(shortcuts)) {
          AnAction action = actionManager.getAction(id);
          xml.append("    <Action id=\"").append(id).append("\">\n");
          Set<String> addedShortcuts = new HashSet<>();
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
              xml.append("      <Text>").append(StringUtil.escapeXmlEntities(text)).append("</Text>\n");
            }
          }
          xml.append("    </Action>\n");
        }
      }
      xml.append("  </Keymap>\n");
    }
    xml.append("</Keymaps>");

    final String path = args.size() == 2 ? args.get(1) : PathManager.getHomePath() + File.separator + "AllKeymaps.xml";

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
