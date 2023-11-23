// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.help.impl;

import com.google.common.collect.Sets;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * @author Konstantin Bulenkov
 */
final class KeymapGenerator implements ApplicationStarter {
  @Override
  public String getCommandName() {
    return "keymap";
  }

  private static final Logger LOG = Logger.getInstance(KeymapGenerator.class);
  private static final String[] LEVELS = IntStream.range(1, 4).mapToObj(i -> " ".repeat(i * 2)).toArray(String[]::new);

  private static void renderAction(@NotNull String id,
                                   @Nullable String asId,
                                   Shortcut @NotNull [] shortcuts,
                                   @NotNull StringBuilder dest) {
    if (shortcuts.length == 0) {
      return;
    }

    dest.append(LEVELS[1]).append("<Action id=\"").append(asId == null ? id : asId).append("\">\n");

    // Different shortcuts may have equal display strings (e.g., shift+minus and shift+subtract)
    // We don't want them do be duplicated for users
    Arrays.stream(shortcuts)
      .map(shortcut -> KeymapUtil.getShortcutText(shortcut))
      .distinct()
      .forEach(shortcut -> dest.append(LEVELS[2]).append("<Shortcut>").append(shortcut).append("</Shortcut>\n"));

    final AnAction action = ActionManager.getInstance().getAction(id);

    if (action != null) {
      final String text = action.getTemplatePresentation().getText();
      if (text != null) {
        dest.append(LEVELS[2]).append("<Text>").append(StringUtil.escapeXmlEntities(text)).append("</Text>\n");
      }
    }
    dest.append(LEVELS[1]).append("</Action>\n");
  }

  @Override
  public void main(@NotNull List<String> args) {
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n").append("<Keymaps>\n");

    KeymapManagerEx keyManager = KeymapManagerEx.getInstanceEx();
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    Set<String> boundActions = actionManager.getBoundActions();

    for (Keymap keymap : keyManager.getAllKeymaps()) {
      xml.append(LEVELS[0]).append("<Keymap name=\"").append(keymap.getPresentableName()).append("\">\n");

      Set<String> alreadyMapped = Sets.newHashSet(keymap.getActionIdList());

      alreadyMapped.forEach(id -> {
        renderAction(id, null, keymap.getShortcuts(id), xml);
      });

      //We need to inject bound actions under their real names in every keymap that doesn't already have them.
      boundActions.forEach(id -> {
        final String binding = actionManager.getActionBinding(id);
        if (binding != null && !alreadyMapped.contains(id)) {
          renderAction(binding, id, keymap.getShortcuts(binding), xml);
        }
      });

      xml.append(LEVELS[0]).append("</Keymap>\n");
    }
    xml.append("</Keymaps>");

    Path targetFilePath = Paths.get(args.size() > 1 ? args.get(1) : PathManager.getHomePath())
      .resolve(
        "keymap-%s.xml".formatted(ApplicationInfoEx.getInstanceEx().getApiVersionAsNumber().getProductCode().toLowerCase(Locale.ROOT)));

    try {
      FileUtil.writeToFile(targetFilePath.toFile(), xml.toString());
      LOG.info("Keymaps saved to: " + targetFilePath.toAbsolutePath());
    }
    catch (IOException e) {
      LOG.error("Cannot save keymaps", e);
      System.exit(1);
    }
    System.exit(0);
  }
}
