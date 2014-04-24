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
package com.intellij.openapi.editor.richcopy.view;

import com.intellij.openapi.editor.richcopy.model.ColorRegistry;
import com.intellij.openapi.editor.richcopy.model.FontNameRegistry;
import com.intellij.openapi.editor.richcopy.model.MarkupHandler;
import com.intellij.openapi.editor.richcopy.model.SyntaxInfo;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;

/**
 * @author Denis Zhdanov
 * @since 3/28/13 1:06 PM
 */
public class HtmlTransferableData extends AbstractSyntaxAwareReaderTransferableData implements MarkupHandler {

  @NotNull public static final DataFlavor FLAVOR = new DataFlavor("text/html;class=java.io.Reader", "HTML text");

  private StringBuilder    myResultBuffer;
  private ColorRegistry    myColorRegistry;
  private FontNameRegistry myFontNameRegistry;

  private int     myForeground;
  private int     myBackground;
  private int     myFontFamily;
  private boolean myBold;
  private boolean myItalic;

  private final TIntObjectHashMap<String> myColors = new TIntObjectHashMap<String>();

  public HtmlTransferableData(@NotNull SyntaxInfo syntaxInfo) {
    super(syntaxInfo, FLAVOR);
  }

  @Override
  protected void build(@NotNull StringBuilder holder, int maxLength) {
    myResultBuffer = holder;
    myColorRegistry = mySyntaxInfo.getColorRegistry();
    myFontNameRegistry = mySyntaxInfo.getFontNameRegistry();
    try {
      buildColorMap();
      myResultBuffer.append("<pre style=\"background-color:");
      appendColor(myResultBuffer, mySyntaxInfo.getDefaultBackground());
      myResultBuffer.append(';');
      if (myFontNameRegistry.size() == 1) {
        appendFontFamilyRule(myResultBuffer, myFontNameRegistry.getAllIds()[0]);
        myFontNameRegistry = null;
      }
      appendFontSizeRule(myResultBuffer, mySyntaxInfo.getSingleFontSize());
      myResultBuffer.append("\" bgcolor=\"");
      appendColor(myResultBuffer, mySyntaxInfo.getDefaultBackground());
      myResultBuffer.append("\">");

      SyntaxInfo.MarkupIterator it = mySyntaxInfo.new MarkupIterator();
      try {
        while(it.hasNext()) {
          it.processNext(this);
          if (myResultBuffer.length() > maxLength) {
            myResultBuffer.append("... truncated ...");
            break;
          }
        }
      }
      finally {
        it.dispose();
      }
      myResultBuffer.append("</pre>");
    }
    finally {
      myResultBuffer = null;
      myColorRegistry = null;
      myFontNameRegistry = null;
      myColors.clear();
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
  
  private void appendColor(StringBuilder builder, int id) {
    builder.append(myColors.get(id));
  }

  private void buildColorMap() {
    for (int id : myColorRegistry.getAllIds()) {
      StringBuilder b = new StringBuilder("#");
      UIUtil.appendColor(myColorRegistry.dataById(id), b);
      myColors.put(id, b.toString());
    }
  }

  private void appendFontFamilyRule(@NotNull StringBuilder styleBuffer, int fontFamilyId) {
    styleBuffer.append("font-family:'").append(myFontNameRegistry.dataById(fontFamilyId)).append("';");
  }

  private static void appendFontSizeRule(@NotNull StringBuilder styleBuffer, int fontSize) {
    styleBuffer.append("font-size:").append(fontSize).append("pt;");
  }

  @Override
  public void handleText(int startOffset, int endOffset) {
    boolean formattedText = myForeground > 0 || myBackground > 0 || myFontFamily > 0 || myBold || myItalic;
    if (!formattedText) {
      escapeAndAdd(startOffset, endOffset);
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
      myResultBuffer.append("<span style=\"");
      myResultBuffer.append(styleBuffer);
      myResultBuffer.append("\">");
      escapeAndAdd(startOffset, endOffset);
      myResultBuffer.append("</span>");
      myResultBuffer.append(closeTagBuffer);
    }
    finally {
      StringBuilderSpinAllocator.dispose(styleBuffer);
      StringBuilderSpinAllocator.dispose(closeTagBuffer);
    }
  }

  private void escapeAndAdd(int start, int end) {
    for (int i = start; i < end; i++) {
      char c = myRawText.charAt(i);
      switch (c) {
        case '<': myResultBuffer.append("&lt;"); break;
        case '>': myResultBuffer.append("&gt;"); break;
        case '&': myResultBuffer.append("&amp;"); break;
        default: myResultBuffer.append(c);
      }
    }
  }

  @Override
  public void handleForeground(int foregroundId) throws Exception {
    myForeground = foregroundId;
  }

  @Override
  public void handleBackground(int backgroundId) throws Exception {
    myBackground = backgroundId;
  }

  @Override
  public void handleFont(int fontNameId) throws Exception {
    if (myFontNameRegistry != null) {
      myFontFamily = fontNameId;
    }
  }

  @Override
  public void handleStyle(int style) throws Exception {
    myBold = (Font.BOLD & style) != 0;
    myItalic = (Font.ITALIC & style) != 0;
  }
}
