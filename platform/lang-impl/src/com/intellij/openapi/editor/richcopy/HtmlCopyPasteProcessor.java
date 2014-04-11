/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.richcopy;

import com.intellij.openapi.editor.richcopy.view.ReaderTransferableData;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 3/28/13 1:05 PM
 */
public class HtmlCopyPasteProcessor extends BaseTextWithMarkupCopyPasteProcessor<ReaderTransferableData> {
  @NotNull public static final DataFlavor FLAVOR = new DataFlavor("text/html;class=java.io.Reader", "HTML text");

  private Map<Color, String> myRenderedColors = new HashMap<Color, String>();

  private Color   myForeground;
  private Color   myBackground;
  private String  myFontFamily;
  private boolean myBold;
  private boolean myItalic;

  public HtmlCopyPasteProcessor(TextWithMarkupProcessor processor) {
    super(processor);
  }

  @Override
  protected void doInit() {
    myForeground = null;
    myBackground = null;
    myFontFamily = null;
    myBold = false;
    myItalic = false;
    myBuilder.append("<div style=\"border:1px inset;padding:2%;\"><pre style=\"margin:0;padding:6px;background-color:");
    appendColor(myBuilder, myDefaultBackground);
    myBuilder.append(';');
    appendFontFamilyRule(myBuilder, myDefaultFontFamily);
    myBuilder.append("font-size:").append(myFontSize).append("pt;");
    myBuilder.append("\" bgcolor=\"");
    appendColor(myBuilder, myDefaultBackground);
    myBuilder.append("\">");
  }

  @Override
  protected void doComplete() {
    myBuilder.append("</pre></div>");
  }

  @Override
  public void setFontFamily(String fontFamily) {
    myFontFamily = myDefaultFontFamily.equals(fontFamily) ? null : fontFamily;
  }

  @Override
  public void setFontStyle(int fontStyle) {
    myBold = (Font.BOLD & fontStyle) != 0;
    myItalic = (Font.ITALIC & fontStyle) != 0;
  }

  @Override
  public void setForeground(Color foreground) {
    myForeground = foreground;
  }

  @Override
  public void setBackground(Color background) {
    myBackground = myDefaultBackground.equals(background) ? null : background;
  }

  @Override
  public void addTextFragment(CharSequence charSequence, int startOffset, int endOffset) {
    boolean formattedText = myForeground != null || myBackground != null || myFontFamily != null || myBold || myItalic;
    if (!formattedText) {
      escapeAndAdd(charSequence, startOffset, endOffset);
      return;
    }

    StringBuilder styleBuffer = StringBuilderSpinAllocator.alloc();
    StringBuilder closeTagBuffer = StringBuilderSpinAllocator.alloc();
    try {
      if (myForeground != null) {
        defineForeground(myForeground, styleBuffer, closeTagBuffer);
      }
      if (myBackground != null) {
        defineBackground(myBackground, styleBuffer, closeTagBuffer);
      }
      if (myBold) {
        defineBold(styleBuffer, closeTagBuffer);
      }
      if (myItalic) {
        defineItalic(styleBuffer, closeTagBuffer);
      }
      if (myFontFamily != null) {
        appendFontFamilyRule(styleBuffer, myFontFamily);
      }
      myBuilder.append("<span style=\"");
      myBuilder.append(styleBuffer);
      myBuilder.append("\">");
      escapeAndAdd(charSequence, startOffset, endOffset);
      myBuilder.append("</span>");
      myBuilder.append(closeTagBuffer);
    }
    finally {
      StringBuilderSpinAllocator.dispose(styleBuffer);
      StringBuilderSpinAllocator.dispose(closeTagBuffer);
    }
  }

  private void defineForeground(Color foreground, @NotNull StringBuilder styleBuffer, @NotNull StringBuilder closeTagBuffer) {
    myBuilder.append("<font color=\"");
    appendColor(myBuilder, foreground);
    myBuilder.append("\">");
    styleBuffer.append("color:");
    appendColor(styleBuffer, foreground);
    styleBuffer.append(";");
    closeTagBuffer.insert(0, "</font>");
  }

  private void defineBackground(Color background, @NotNull StringBuilder styleBuffer, @NotNull StringBuilder closeTagBuffer) {
    myBuilder.append("<font bgcolor=\"");
    appendColor(myBuilder, background);
    myBuilder.append("\">");
    styleBuffer.append("background-color:");
    appendColor(styleBuffer, background);
    styleBuffer.append(";");
    closeTagBuffer.insert(0, "</font>");
  }

  private void defineBold(@NotNull StringBuilder styleBuffer, @NotNull StringBuilder closeTagBuffer) {
    myBuilder.append("<b>");
    styleBuffer.append("font-weight:bold;");
    closeTagBuffer.insert(0, "</b>");
  }

  private void defineItalic(@NotNull StringBuilder styleBuffer, @NotNull StringBuilder closeTagBuffer) {
    myBuilder.append("<i>");
    styleBuffer.append("font-style:italic;");
    closeTagBuffer.insert(0, "</i>");
  }

  private void appendColor(StringBuilder builder, Color color) {
    String colorAsString = myRenderedColors.get(color);
    if (colorAsString == null) {
      StringBuilder b = StringBuilderSpinAllocator.alloc();
      try {
        b.append('#');
        UIUtil.appendColor(color, b);
        colorAsString = b.toString();
        myRenderedColors.put(color, colorAsString);
      }
      finally {
        StringBuilderSpinAllocator.dispose(b);
      }
    }
    builder.append(colorAsString);
  }

  private static void appendFontFamilyRule(@NotNull StringBuilder styleBuffer, String fontFamily) {
    styleBuffer.append("font-family:'").append(fontFamily).append("';");
  }

  private void escapeAndAdd(CharSequence charSequence, int start, int end) {
    for (int i = start; i < end; i++) {
      char c = charSequence.charAt(i);
      switch (c) {
        case '<': myBuilder.append("&lt;"); break;
        case '>': myBuilder.append("&gt;"); break;
        case '&': myBuilder.append("&amp;"); break;
        default: myBuilder.append(c);
      }
    }
  }

  @Override
  protected ReaderTransferableData createTransferable(@NotNull String data) {
    return new ReaderTransferableData(data, FLAVOR);
  }
}
