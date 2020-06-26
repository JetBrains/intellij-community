// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class TextDiffTypeFactory {
  @NotNull public static final TextDiffTypeImpl INSERTED =
    new TextDiffTypeImpl(DiffColors.DIFF_INSERTED, DiffBundle.message("diff.type.inserted.name"));
  @NotNull public static final TextDiffTypeImpl DELETED =
    new TextDiffTypeImpl(DiffColors.DIFF_DELETED, DiffBundle.message("diff.type.deleted.name"));
  @NotNull public static final TextDiffTypeImpl MODIFIED =
    new TextDiffTypeImpl(DiffColors.DIFF_MODIFIED, DiffBundle.message("diff.type.changed.name"));
  @NotNull public static final TextDiffTypeImpl CONFLICT =
    new TextDiffTypeImpl(DiffColors.DIFF_CONFLICT, DiffBundle.message("diff.type.conflict.name"));

  private static final TextDiffTypeFactory ourInstance = new TextDiffTypeFactory();
  private final List<TextDiffTypeImpl> myTypes = new ArrayList<>();

  private TextDiffTypeFactory() {
    ContainerUtil.addAll(myTypes, INSERTED, DELETED, MODIFIED, CONFLICT);
  }

  @NotNull
  public synchronized TextDiffType createTextDiffType(@NonNls @NotNull TextAttributesKey key,
                                                      @NotNull String name) {
    TextDiffTypeImpl type = new TextDiffTypeImpl(key, name);
    myTypes.add(type);
    return type;
  }

  public synchronized TextDiffTypeImpl[] getAllDiffTypes() {
    return myTypes.toArray(new TextDiffTypeImpl[0]);
  }

  public static TextDiffTypeFactory getInstance() {
    return ourInstance;
  }

  public static class TextDiffTypeImpl implements TextDiffType {
    @NotNull private final TextAttributesKey myKey;
    @NotNull private final String myName;

    public TextDiffTypeImpl(@NotNull TextAttributesKey key, @NotNull String name) {
      myKey = key;
      myName = name;
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @NotNull
    public TextAttributes getAttributes(@Nullable Editor editor) {
      if (editor == null) {
        return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(myKey);
      }
      else {
        return editor.getColorsScheme().getAttributes(myKey);
      }
    }

    @NotNull
    @Override
    public Color getColor(@Nullable Editor editor) {
      return ObjectUtils.notNull(getAttributes(editor).getBackgroundColor(), JBColor.DARK_GRAY);
    }

    @NotNull
    @Override
    public Color getIgnoredColor(@Nullable Editor editor) {
      Color color = getAttributes(editor).getForegroundColor();
      if (color != null) return color;

      Color fg = getColor(editor);
      Color bg = editor instanceof EditorEx
                 ? ((EditorEx)editor).getBackgroundColor()
                 : EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
      return ColorUtil.mix(fg, bg, MIDDLE_COLOR_FACTOR);
    }

    @Nullable
    @Override
    public Color getMarkerColor(@Nullable Editor editor) {
      return getAttributes(editor).getErrorStripeColor();
    }

    @NotNull
    public TextAttributesKey getKey() {
      return myKey;
    }

    @Override
    public String toString() {
      return myName;
    }
  }

  private static final double MIDDLE_COLOR_FACTOR = 0.6;

  @NotNull
  public static Color getMiddleColor(@NotNull Color fg, @NotNull Color bg) {
    return ColorUtil.mix(fg, bg, MIDDLE_COLOR_FACTOR);
  }
}
