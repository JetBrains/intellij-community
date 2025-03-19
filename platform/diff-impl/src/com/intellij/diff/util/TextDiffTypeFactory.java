// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util;

import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

public final class TextDiffTypeFactory {
  public static final @NotNull TextDiffTypeImpl INSERTED =
    new TextDiffTypeImpl(DiffColors.DIFF_INSERTED, DiffBundle.message("diff.type.inserted.name"));
  public static final @NotNull TextDiffTypeImpl DELETED =
    new TextDiffTypeImpl(DiffColors.DIFF_DELETED, DiffBundle.message("diff.type.deleted.name"));
  public static final @NotNull TextDiffTypeImpl MODIFIED =
    new TextDiffTypeImpl(DiffColors.DIFF_MODIFIED, DiffBundle.message("diff.type.changed.name"));
  public static final @NotNull TextDiffTypeImpl CONFLICT =
    new TextDiffTypeImpl(DiffColors.DIFF_CONFLICT, DiffBundle.message("diff.type.conflict.name"));

  public static @NotNull List<TextDiffTypeImpl> getAllDiffTypes() {
    return Arrays.asList(INSERTED, DELETED, MODIFIED, CONFLICT);
  }

  public static class TextDiffTypeImpl implements TextDiffType {
    private final @NotNull TextAttributesKey myKey;
    private final @NotNull @Nls String myName;

    public TextDiffTypeImpl(@NotNull TextAttributesKey key, @NotNull @Nls String name) {
      myKey = key;
      myName = name;
    }

    @Override
    public @Nls @NotNull String getName() {
      return myName;
    }

    public @NotNull TextAttributes getAttributes(@Nullable Editor editor) {
      if (editor == null) {
        return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(myKey);
      }
      else {
        return editor.getColorsScheme().getAttributes(myKey);
      }
    }

    @Override
    public @NotNull Color getColor(@Nullable Editor editor) {
      return ObjectUtils.notNull(getAttributes(editor).getBackgroundColor(), JBColor.DARK_GRAY);
    }

    @Override
    public @NotNull Color getIgnoredColor(@Nullable Editor editor) {
      Color color = getAttributes(editor).getForegroundColor();
      if (color != null) return color;

      Color fg = getColor(editor);
      Color bg = editor instanceof EditorEx
                 ? ((EditorEx)editor).getBackgroundColor()
                 : EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
      return ColorUtil.mix(fg, bg, MIDDLE_COLOR_FACTOR);
    }

    @Override
    public @Nullable Color getMarkerColor(@Nullable Editor editor) {
      return getAttributes(editor).getErrorStripeColor();
    }

    public @NotNull TextAttributesKey getKey() {
      return myKey;
    }

    @Override
    public String toString() {
      return myName;
    }
  }

  private static final double MIDDLE_COLOR_FACTOR = 0.6;

  public static @NotNull Color getMiddleColor(@NotNull Color fg, @NotNull Color bg) {
    return ColorUtil.mix(fg, bg, MIDDLE_COLOR_FACTOR);
  }
}
