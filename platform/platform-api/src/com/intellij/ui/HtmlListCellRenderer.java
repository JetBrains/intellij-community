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
package com.intellij.ui;

import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.roots.ui.CellAppearanceEx;
import com.intellij.openapi.roots.ui.util.ModifiableCellAppearance;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * <p>Replacement for {@link ColoredListCellRenderer}, L&F friendly.
 *
 * <p>From {@link #customize(javax.swing.JList, Object, int, boolean, boolean)},
 * text fragments should be added via {@link #append(String, SimpleTextAttributes)}.<br>
 * Note that not all styles from SimpleTextAttributes are supported, see method code for details.
 *
 * <p>Alternatively, item can be rendered by {@link com.intellij.openapi.roots.ui.CellAppearanceEx#customize(HtmlListCellRenderer)}.
 *
 * @param <T> type of list items.
 */
public abstract class HtmlListCellRenderer<T> extends ListCellRendererWrapper<T> {
  private StringBuilder myText;

  /**
   * Default JComboBox cell renderer should be passed here.
   * @param listCellRenderer Default cell renderer ({@link javax.swing.JComboBox#getRenderer()}).
   */
  public HtmlListCellRenderer(final ListCellRenderer listCellRenderer) {
    super(listCellRenderer);
  }

  @Override
  public final void customize(final JList list, final T value, final int index, final boolean selected, final boolean hasFocus) {
    myText = StringBuilderSpinAllocator.alloc();
    try {
      doCustomize(list, value, index, selected, hasFocus);

      if (myText.length() == 0) {
        setText(null);
      }
      else {
        myText.insert(0, "<html><body style=\"white-space:nowrap\">");
        myText.append("</body></html>");
        setText(myText.toString());
      }
    }
    finally {
      StringBuilderSpinAllocator.dispose(myText);
      myText = null;
    }
  }

  protected abstract void doCustomize(final JList list, final T value, final int index, final boolean selected, final boolean hasFocus);

  public void append(@NotNull final String fragment) {
    append(fragment, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public void append(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes) {
    if (fragment.length() > 0) {
      myText.append("<span ");
      formatStyle(myText, attributes);
      myText.append('>').append(StringUtil.escapeXml(fragment)).append("</span>");
    }
  }

  public void appendLink(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes, @NotNull final String url) {
    if (fragment.length() > 0) {
      myText.append("<a href=\"").append(StringUtil.replace(url, "\"", "%22")).append("\" ");
      formatStyle(myText, attributes);
      myText.append('>').append(StringUtil.escapeXml(fragment)).append("</a>");
    }
  }

  public void append(SimpleColoredText text) {
    int length = text.getTexts().size();
    for (int i = 0; i < length; i++) {
      String fragment = text.getTexts().get(i);
      SimpleTextAttributes attributes = text.getAttributes().get(i);
      append(fragment, attributes);
    }
  }

  private static void formatStyle(final StringBuilder builder, final SimpleTextAttributes attributes) {
    final Color fgColor = attributes.getFgColor();
    final Color bgColor = attributes.getBgColor();
    final int style = attributes.getStyle();

    builder.append("style=\"");
    if (fgColor != null) {
      builder.append("color:#").append(Integer.toString(fgColor.getRGB() & 0xFFFFFF, 16)).append(';');
    }
    if (bgColor != null) {
      builder.append("background-color:#").append(Integer.toString(bgColor.getRGB() & 0xFFFFFF, 16)).append(';');
    }
    if ((style & SimpleTextAttributes.STYLE_BOLD) != 0) {
      builder.append("font-weight:bold;");
    }
    if ((style & SimpleTextAttributes.STYLE_ITALIC) != 0) {
      builder.append("font-style:italic;");
    }
    if ((style & SimpleTextAttributes.STYLE_UNDERLINE) != 0) {
      builder.append("text-decoration:underline;");
    }
    else if ((style & SimpleTextAttributes.STYLE_STRIKEOUT) != 0) {
      builder.append("text-decoration:line-through;");
    }
    builder.append('"');
  }

  /** @deprecated use {@linkplain com.intellij.openapi.roots.ui.CellAppearanceEx#customize(HtmlListCellRenderer)} (to remove in IDEA 12) */
  public void render(@NotNull final ModifiableCellAppearance appearance) {
    ((CellAppearanceEx)appearance).customize(this);
  }
}
