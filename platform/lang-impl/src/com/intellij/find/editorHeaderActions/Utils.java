// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.editorHeaderActions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.ui.popup.AlignedPopup;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.List;

import static com.intellij.ui.dsl.listCellRenderer.BuilderKt.textListCellRenderer;

public final class Utils {
  private Utils() {
  }

  public static void showCompletionPopup(JComponent toolbarComponent,
                                         JList<String> list,
                                         @NlsContexts.PopupTitle String title,
                                         JTextComponent textField,
                                         @NlsContexts.PopupAdvertisement String ad) {
    showCompletionPopup(toolbarComponent, list, title, textField, ad, () -> {
    });
  }

  @ApiStatus.Internal
  public static void showCompletionPopup(JComponent toolbarComponent,
                                         JList<String> list,
                                         @NlsContexts.PopupTitle String title,
                                         JTextComponent textField,
                                         @NlsContexts.PopupAdvertisement String ad,
                                         @NotNull Runnable onSelect) {

    final Runnable callback = () -> {
      String selectedValue = list.getSelectedValue();
      if (selectedValue != null) {
        textField.setText(selectedValue);
        IdeFocusManager.getGlobalInstance().requestFocus(textField, false);
        onSelect.run();
      }
    };

    final PopupChooserBuilder<String> builder = JBPopupFactory.getInstance().createListPopupBuilder(list);
    if (title != null) {
      builder.setTitle(title);
    }
    final JBPopup popup = builder
      .setMovable(false)
      .setResizable(false)
      .setRequestFocus(true)
      .setItemChosenCallback(callback)
      .setRenderer(textListCellRenderer((@Nls String s) -> s))
      .createPopup();

    if (ad != null) {
      popup.setAdText(ad, SwingConstants.LEFT);
    }

    JComponent parent = toolbarComponent != null ? toolbarComponent : textField;
    AlignedPopup.showUnderneathWithoutAlignment(popup, parent);
  }

  public static void setSmallerFont(JComponent component) {
    if (SystemInfo.isMac) {
      component.setFont(JBUI.Fonts.smallFont());
    }
  }

  public static void setSmallerFontForChildren(JComponent component) {
    for (Component c : component.getComponents()) {
      if (c instanceof JComponent) {
        setSmallerFont((JComponent)c);
      }
    }
  }

  public static @NotNull CustomShortcutSet shortcutSetOf(@NotNull List<Shortcut> shortcuts) {
    return new CustomShortcutSet(shortcuts.toArray(Shortcut.EMPTY_ARRAY));
  }

  public static @NotNull List<Shortcut> shortcutsOf(@NotNull String actionId) {
    AnAction action = ActionManager.getInstance().getAction(actionId);
    return action == null ? ContainerUtil.emptyList() : List.of(action.getShortcutSet().getShortcuts());
  }
}
