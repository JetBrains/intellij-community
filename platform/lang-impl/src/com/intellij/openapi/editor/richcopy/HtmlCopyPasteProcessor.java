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

import com.intellij.openapi.editor.richcopy.model.*;
import com.intellij.openapi.editor.richcopy.view.ReaderTransferableData;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;

/**
 * @author Denis Zhdanov
 * @since 3/28/13 1:05 PM
 */
public class HtmlCopyPasteProcessor extends BaseTextWithMarkupCopyPasteProcessor<ReaderTransferableData> implements OutputInfoVisitor {
  @NotNull public static final DataFlavor FLAVOR = new DataFlavor("text/html;class=java.io.Reader", "HTML text");

  private StringBuilder    myResultBuffer;
  private CharSequence     myRawText;
  private ColorRegistry    myColorRegistry;
  private FontNameRegistry myFontNameRegistry;
  private TIntObjectHashMap<String> myColors = new TIntObjectHashMap<String>();

  private int     myForeground;
  private int     myBackground;
  private int     myFontFamily;
  private int     myFontSize;
  private boolean myBold;
  private boolean myItalic;
  private boolean myIgnoreFontSize;

  public HtmlCopyPasteProcessor(TextWithMarkupProcessor processor) {
    super(processor);
  }

  @Override
  protected void buildStringRepresentation(@NotNull StringBuilder buffer,
                                           @NotNull CharSequence rawText,
                                           @NotNull SyntaxInfo syntaxInfo,
                                           int maxLength) {
    myResultBuffer = buffer;
    myRawText = rawText;
    myColorRegistry = syntaxInfo.getColorRegistry();
    myFontNameRegistry = syntaxInfo.getFontNameRegistry();
    buildColorMap();
    try {
      myResultBuffer.append("<div style=\"border:1px inset;padding:2%;\">")
        .append("<pre style=\"margin:0;padding:6px;background-color:");
      //              .append("<pre style='height:30%;overflow:auto;margin:0;padding:6px;background-color:")
      appendColor(myResultBuffer, syntaxInfo.getDefaultBackground());
      myResultBuffer.append(';');
      if (myFontNameRegistry.size() == 1) {
        appendFontFamilyRule(myResultBuffer, myFontNameRegistry.getAllIds()[0]);
        myFontNameRegistry = null;
      }
      int fontSize = syntaxInfo.getSingleFontSize();
      if (fontSize > 0) {
        appendFontSizeRule(myResultBuffer, fontSize);
        myIgnoreFontSize = true;
      }
      myResultBuffer.append("\" bgcolor=\"");
      appendColor(myResultBuffer, syntaxInfo.getDefaultBackground());
      myResultBuffer.append("\">");

      for (OutputInfo info : syntaxInfo.getOutputInfos()) {
        info.invite(this);
        if (myResultBuffer.length() > maxLength) {
          myResultBuffer.append("... truncated ...");
          break;
        }
      }
      myResultBuffer.append("</pre></div>");
    }
    finally {
      myResultBuffer = null;
      myRawText = null;
      myColorRegistry = null;
      myFontNameRegistry = null;
      myColors.clear();
      myForeground = 0;
      myBackground = 0;
      myFontFamily = 0;
      myFontSize = 0;
      myBold = false;
      myItalic = false;
      myIgnoreFontSize = false;
    }
  }

  private void defineForeground(int id, @NotNull StringBuilder styleBuffer, @NotNull StringBuilder closeTagBuffer) {
    myResultBuffer.append("<font color=\"");
    appendColor(myResultBuffer, id);
    myResultBuffer.append("\">");
    styleBuffer.append("color:");
    appendColor(styleBuffer, id);
    styleBuffer.append(";");
    closeTagBuffer.insert(0, "</font>");
  }

  private void defineBackground(int id, @NotNull StringBuilder styleBuffer, @NotNull StringBuilder closeTagBuffer) {
    myResultBuffer.append("<font bgcolor=\"");
    appendColor(myResultBuffer, id);
    myResultBuffer.append("\">");
    styleBuffer.append("background-color:");
    appendColor(styleBuffer, id);
    styleBuffer.append(";");
    closeTagBuffer.insert(0, "</font>");
  }

  private void defineBold(@NotNull StringBuilder styleBuffer, @NotNull StringBuilder closeTagBuffer) {
    myResultBuffer.append("<b>");
    styleBuffer.append("font-weight:bold;");
    closeTagBuffer.insert(0, "</b>");
  }

  private void defineItalic(@NotNull StringBuilder styleBuffer, @NotNull StringBuilder closeTagBuffer) {
    myResultBuffer.append("<i>");
    styleBuffer.append("font-style:italic;");
    closeTagBuffer.insert(0, "</i>");
  }

  private void buildColorMap() {
    for (int id : myColorRegistry.getAllIds()) {
      StringBuilder b = new StringBuilder("#");
      UIUtil.appendColor(myColorRegistry.dataById(id), b);
      myColors.put(id, b.toString());
    }
  }

  private void appendColor(StringBuilder builder, int id) {
    builder.append(myColors.get(id));
  }

  private void appendFontFamilyRule(@NotNull StringBuilder styleBuffer, int fontFamilyId) {
    styleBuffer.append("font-family:'").append(myFontNameRegistry.dataById(fontFamilyId)).append("';");
  }

  private static void appendFontSizeRule(@NotNull StringBuilder styleBuffer, int fontSize) {
    styleBuffer.append("font-size:").append(fontSize).append(';');
  }

  @Override
  public void visit(@NotNull Text text) {
    boolean formattedText = myForeground > 0 || myBackground > 0 || myFontFamily > 0 || myFontSize > 0 || myBold || myItalic;
    if (!formattedText) {
      escapeAndAdd(text);
      return;
    }

    StringBuilder styleBuffer = StringBuilderSpinAllocator.alloc();
    StringBuilder closeTagBuffer = StringBuilderSpinAllocator.alloc();
    try {
      if (myForeground > 0) {
        defineForeground(myForeground, styleBuffer, closeTagBuffer);
      }
      if (myBackground > 0) {
        defineBackground(myBackground, styleBuffer, closeTagBuffer);
      }
      if (myBold) {
        defineBold(styleBuffer, closeTagBuffer);
      }
      if (myItalic) {
        defineItalic(styleBuffer, closeTagBuffer);
      }
      if (myFontFamily > 0) {
        appendFontFamilyRule(styleBuffer, myFontFamily);
      }
      if (myFontSize > 0) {
        appendFontSizeRule(styleBuffer, myFontSize);
      }
      myResultBuffer.append("<span style=\"");
      myResultBuffer.append(styleBuffer);
      myResultBuffer.append("\">");
      escapeAndAdd(text);
      myResultBuffer.append("</span>");
      myResultBuffer.append(closeTagBuffer);
    }
    finally {
      StringBuilderSpinAllocator.dispose(styleBuffer);
      StringBuilderSpinAllocator.dispose(closeTagBuffer);
    }
  }

  private void escapeAndAdd(Text text) {
    int start = text.getStartOffset();
    int end = text.getEndOffset();
    for (int i = start; i < end; i++) {
      char c = text.getCharAt(myRawText, i);
      switch (c) {
        case '<': myResultBuffer.append("&lt;"); break;
        case '>': myResultBuffer.append("&gt;"); break;
        case '&': myResultBuffer.append("&amp;"); break;
        default: myResultBuffer.append(c);
      }
    }
  }

  @Override
  public void visit(@NotNull Foreground color) {
    myForeground = color.getId();
  }

  @Override
  public void visit(@NotNull Background color) {
    myBackground = color.getId();
  }

  @Override
  public void visit(@NotNull FontFamilyName name) {
    if (myFontNameRegistry != null) {
      myFontFamily = name.getId();
    }
  }

  @Override
  public void visit(@NotNull FontStyle style) {
    myBold = (Font.BOLD & style.getStyle()) != 0;
    myItalic = (Font.ITALIC & style.getStyle()) != 0;
  }

  @Override
  public void visit(@NotNull FontSize size) {
    if (!myIgnoreFontSize) {
      myFontSize = size.getSize();
    }
  }

  @Override
  protected ReaderTransferableData createTransferable(@NotNull String data) {
    return new ReaderTransferableData(data, FLAVOR);
  }
}
