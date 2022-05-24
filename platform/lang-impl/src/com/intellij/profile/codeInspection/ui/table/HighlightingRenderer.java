// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui.table;

import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.application.options.colors.ColorSettingsUtil;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.ide.DataManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.ui.ComboBoxTableRenderer;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.util.Pair;
import com.intellij.profile.codeInspection.ui.HighlightingChooser;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;
import java.util.EventObject;
import java.util.List;

public class HighlightingRenderer extends ComboBoxTableRenderer<TextAttributesKey> {

  private static List<Pair<TextAttributesKey, String>> myTextAttributes;

  public static final TextAttributesKey EDIT_HIGHLIGHTING = TextAttributesKey.createTextAttributesKey("-");


  public HighlightingRenderer() {
    super(getAttributeKeys());
  }

  @Override
  protected String getTextFor(@NotNull TextAttributesKey value) {
    String text = value.getExternalName();
    for (Pair<TextAttributesKey, @Nls String> pair: myTextAttributes) {
      if (value == pair.first) {
        text = pair.second;
        break;
      }
    }
    text = HighlightingChooser.stripColorOptionCategory(text);
    return text;
  }

  @Override
  public boolean isCellEditable(EventObject event) {
    return !(event instanceof MouseEvent) || ((MouseEvent)event).getClickCount() >= 1;
  }

  @Override
  protected ListSeparator getSeparatorAbove(TextAttributesKey value) {
    return value == EDIT_HIGHLIGHTING ? new ListSeparator() : null;
  }

  private static TextAttributesKey[] getAttributeKeys() {
    myTextAttributes = ColorSettingsUtil.getErrorTextAttributes();
    myTextAttributes.add(new Pair<>(EDIT_HIGHLIGHTING, InspectionsBundle.message("inspection.edit.highlighting.action")));
    return myTextAttributes.stream()
      .map(pair -> pair.first)
      .toArray(TextAttributesKey[]::new);
  }

  @Override
  public void onClosed(@NotNull LightweightWindowEvent event) {
    super.onClosed(event);
    if (getCellEditorValue() == EDIT_HIGHLIGHTING) {
      ApplicationManager.getApplication().invokeLater(() -> {
          // TODO: Open a popup with ColorAndFontOptions
      });
    }
  }
}
