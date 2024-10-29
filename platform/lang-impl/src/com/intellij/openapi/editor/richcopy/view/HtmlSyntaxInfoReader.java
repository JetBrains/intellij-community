// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.richcopy.view;

import com.intellij.openapi.editor.richcopy.FontMapper;
import com.intellij.openapi.editor.richcopy.model.ColorRegistry;
import com.intellij.openapi.editor.richcopy.model.FontNameRegistry;
import com.intellij.openapi.editor.richcopy.model.MarkupHandler;
import com.intellij.openapi.editor.richcopy.model.SyntaxInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

@ApiStatus.Internal
public class HtmlSyntaxInfoReader extends AbstractSyntaxAwareReader implements MarkupHandler {
  private final int myTabSize;
  protected StringBuilder    myResultBuffer;
  private ColorRegistry    myColorRegistry;
  private FontNameRegistry myFontNameRegistry;
  private int myMaxLength;

  private int     myDefaultForeground;
  private int     myDefaultBackground;
  private int     myDefaultFontFamily;
  private int     myForeground;
  private int     myBackground;
  private int     myFontFamily;
  private boolean myBold;
  private boolean myItalic;
  private int     myCurrentColumn;

  private final Int2ObjectMap<String> myColors = new Int2ObjectOpenHashMap<>();

  public HtmlSyntaxInfoReader(@NotNull SyntaxInfo syntaxInfo, int tabSize) {
    super(syntaxInfo);
    myTabSize = tabSize;
  }

  @Override
  protected void build(@NotNull StringBuilder holder, int maxLength) {
    myResultBuffer = holder;
    myColorRegistry = mySyntaxInfo.getColorRegistry();
    myFontNameRegistry = mySyntaxInfo.getFontNameRegistry();
    myDefaultForeground = myForeground = mySyntaxInfo.getDefaultForeground();
    myDefaultBackground = myBackground = mySyntaxInfo.getDefaultBackground();
    myBold = myItalic = false;
    myCurrentColumn = 0;
    myMaxLength = maxLength;
    try {
      buildColorMap();
      appendStartTags();

      mySyntaxInfo.processOutputInfo(this);

      appendCloseTags();
    }
    finally {
      myResultBuffer = null;
      myColorRegistry = null;
      myFontNameRegistry = null;
      myColors.clear();
    }
  }

  protected void appendCloseTags() {
    myResultBuffer.append("</pre></div></body></html>");
  }

  protected void appendStartTags() {
    myResultBuffer.append("<html><head><meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\"></head><body>");

    // IDEA-295350 background color is applied to a div block in order to be displayed in Google Docs correctly
    myResultBuffer.append("<div style=\"background-color:");
    appendColor(myResultBuffer, myDefaultBackground);
    myResultBuffer.append(";color:");
    appendColor(myResultBuffer, myDefaultForeground);
    myResultBuffer.append("\">");

    // the pre tag is used instead of `white-space:pre;` because of IDEA-316921
    myResultBuffer.append("<pre style=\"");
    int[] fontIds = myFontNameRegistry.getAllIds();
    if (fontIds.length > 0) {
      myFontFamily = myDefaultFontFamily = fontIds[0];
      appendFontFamilyRule(myResultBuffer, myDefaultFontFamily);
    }
    else {
      myFontFamily = myDefaultFontFamily = -1;
    }
    float fontSize = mySyntaxInfo.getFontSize();
    // on macOS font size in points declared in HTML doesn't mean the same value as when declared e.g. in TextEdit (and in Java),
    // this is the correction factor
    if (SystemInfo.isMac) fontSize *= 0.75f;
    myResultBuffer.append(String.format("font-size:%.1fpt;", fontSize));
    myResultBuffer.append("\">");
  }

  protected void appendFontFamilyRule(@NotNull StringBuilder styleBuffer, int fontFamilyId) {
    String fontName = myFontNameRegistry.dataById(fontFamilyId);
    styleBuffer.append("font-family:'").append(fontName).append('\'');
    if (FontMapper.isMonospaced(fontName)) {
      styleBuffer.append(",monospace");
    }
    styleBuffer.append(';');
  }

  private static void defineBold(@NotNull StringBuilder styleBuffer) {
    styleBuffer.append("font-weight:bold;");
  }

  private static void defineItalic(@NotNull StringBuilder styleBuffer) {
    styleBuffer.append("font-style:italic;");
  }

  private void defineForeground(int id, @NotNull StringBuilder styleBuffer) {
    styleBuffer.append("color:");
    appendColor(styleBuffer, id);
    styleBuffer.append(";");
  }

  protected void defineBackground(int id, @NotNull StringBuilder styleBuffer) {
    styleBuffer.append("background-color:");
    appendColor(styleBuffer, id);
    styleBuffer.append(";");
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

  @Override
  public void handleText(int startOffset, int endOffset) {
    boolean formattedText = myForeground != myDefaultForeground || myBackground != myDefaultBackground || myFontFamily != myDefaultFontFamily || myBold || myItalic;
    if (!formattedText) {
      escapeAndAdd(startOffset, endOffset);
      return;
    }

    myResultBuffer.append("<span style=\"");
    if (myForeground != myDefaultForeground) {
      defineForeground(myForeground, myResultBuffer);
    }
    if (myBackground != myDefaultBackground) {
      defineBackground(myBackground, myResultBuffer);
    }
    if (myBold) {
      defineBold(myResultBuffer);
    }
    if (myItalic) {
      defineItalic(myResultBuffer);
    }
    if (myFontFamily != myDefaultFontFamily) {
      appendFontFamilyRule(myResultBuffer, myFontFamily);
    }
    myResultBuffer.append("\">");
    escapeAndAdd(startOffset, endOffset);
    myResultBuffer.append("</span>");
  }

  private void escapeAndAdd(int start, int end) {
    for (int i = start; i < end; i++) {
      char c = myRawText.charAt(i);
      switch (c) {
        case '<' -> myResultBuffer.append("&lt;");
        case '>' -> myResultBuffer.append("&gt;");
        case '&' -> myResultBuffer.append("&amp;");
        case ' ' -> myResultBuffer.append("&#32;");
        case '\n' -> {
          myResultBuffer.append("<br>");
          myCurrentColumn = -1; // -1 is because we increment myCurrentColumn right after the switch
        }
        case '\t' -> {
          int newColumn = (myCurrentColumn / myTabSize + 1) * myTabSize;
          for (; myCurrentColumn < newColumn; myCurrentColumn++) myResultBuffer.append("&#32;");
        }
        default -> myResultBuffer.append(c);
      }
      myCurrentColumn++;
    }
  }

  @Override
  public void handleForeground(int foregroundId) {
    myForeground = foregroundId;
  }

  @Override
  public void handleBackground(int backgroundId) {
    myBackground = backgroundId;
  }

  @Override
  public void handleFont(int fontNameId) {
    myFontFamily = fontNameId;
  }

  @Override
  public void handleStyle(int style) {
    myBold = (Font.BOLD & style) != 0;
    myItalic = (Font.ITALIC & style) != 0;
  }

  @Override
  public boolean canHandleMore() {
    if (myResultBuffer.length() > myMaxLength) {
      myResultBuffer.append("... truncated ...");
      return false;
    }
    return true;
  }
}
