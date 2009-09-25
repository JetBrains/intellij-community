/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.tree;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 27, 2007
 */
public class ValueMarkup {
  public static final long AUTO_MARKUP_REFERRING_OBJECTS_LIMIT = 100L; // todo: some reasonable limit

  private final String myText;
  private final Color myColor;
  @Nullable
  private final String myToolTipText;

  public ValueMarkup(final String text, final Color color) {
    this(text, color, null);
  }

  public ValueMarkup(final String text, final Color color, String toolTipText) {
    myText = text;
    myColor = color;
    myToolTipText = toolTipText;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  public Color getColor() {
    return myColor;
  }

  @Nullable
  public String getToolTipText() {
    return myToolTipText;
  }

  public static Color getAutoMarkupColor() {
    final EditorColorsManager manager = EditorColorsManager.getInstance();
    final TextAttributes textAttributes = manager.getGlobalScheme().getAttributes(HighlightInfoType.STATIC_FIELD.getAttributesKey());
    return textAttributes.getForegroundColor();
  }
}
