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
package com.intellij.diff.util;

import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.ColorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TextDiffTypeFactory {
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
    return myTypes.toArray(new TextDiffTypeImpl[myTypes.size()]);
  }

  public static TextDiffTypeFactory getInstance() {
    return ourInstance;
  }

  public static class TextDiffTypeImpl implements TextDiffType {
    @NotNull private final TextAttributesKey myKey;
    @NotNull private final String myName;

    @SuppressWarnings("SpellCheckingInspection")
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
      return getAttributes(editor).getBackgroundColor();
    }

    @NotNull
    @Override
    public Color getIgnoredColor(@Nullable Editor editor) {
      TextAttributes attributes = getAttributes(editor);
      Color color = attributes.getForegroundColor();
      if (color != null) return color;

      if (editor instanceof EditorEx) {
        Color fg = attributes.getBackgroundColor();
        Color bg = ((EditorEx)editor).getBackgroundColor();
        return ColorUtil.mix(fg, bg, MIDDLE_COLOR_FACTOR);
      }
      else {
        Color fg = attributes.getBackgroundColor();
        Color bg = EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
        return ColorUtil.mix(fg, bg, MIDDLE_COLOR_FACTOR);
      }
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
