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
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;

public class RtfTransferableData extends AbstractSyntaxAwareInputStreamTransferableData {

  @NotNull public static final DataFlavor FLAVOR = new DataFlavor("text/rtf;class=java.io.InputStream", "RTF text");

  @NotNull private static final String HEADER_PREFIX = "{\\rtf1\\ansi\\deff0";
  @NotNull private static final String HEADER_SUFFIX = "}";
  @NotNull private static final String TAB           = "\\tab\n";
  @NotNull private static final String NEW_LINE      = "\\line\n";
  @NotNull private static final String BOLD          = "\\b";
  @NotNull private static final String ITALIC        = "\\i";

  public RtfTransferableData(@NotNull SyntaxInfo syntaxInfo) {
    super(syntaxInfo, FLAVOR);
  }

  @Override
  protected void build(@NotNull final StringBuilder holder, final int maxLength) {
    holder.append(HEADER_PREFIX);

    holder.append("{\\colortbl;");
    ColorRegistry colorRegistry = mySyntaxInfo.getColorRegistry();
    for (int id : colorRegistry.getAllIds()) {
      Color color = colorRegistry.dataById(id);
      holder.append(String.format("\\red%d\\green%d\\blue%d;", color.getRed(), color.getGreen(), color.getBlue()));
    }
    holder.append("}\n");

    holder.append("{\\fonttbl");
    FontNameRegistry fontNameRegistry = mySyntaxInfo.getFontNameRegistry();
    for (int id : fontNameRegistry.getAllIds()) {
      String fontName = fontNameRegistry.dataById(id);
      holder.append(String.format("{\\f%d %s;}", id, fontName));
    }
    holder.append("}\n");

    holder.append("\n\\s0\\box")
      .append("\\cbpat").append(mySyntaxInfo.getDefaultBackground())
      .append("\\cb").append(mySyntaxInfo.getDefaultBackground());
    addFontSize(holder, mySyntaxInfo.getFontSize());
    holder.append('\n');

    mySyntaxInfo.processOutputInfo(new MyVisitor(holder, myRawText, mySyntaxInfo, maxLength));

    holder.append("\\par");
    holder.append(HEADER_SUFFIX);
  }

  @NotNull
  @Override
  protected String getCharset() {
    return "US-ASCII";
  }

  private static void addFontSize(StringBuilder buffer, float fontSize) {
    buffer.append("\\fs").append(Math.round(fontSize * 2));
  }

  private static class MyVisitor implements MarkupHandler {

    @NotNull private final StringBuilder myBuffer;
    @NotNull private final String        myRawText;
    private final int myMaxLength;

    private final int myDefaultBackgroundId;
    private final float myFontSize;
    private int myForegroundId = -1;
    private int myFontNameId   = -1;
    private int myFontStyle    = -1;

    MyVisitor(@NotNull StringBuilder buffer, @NotNull String rawText, @NotNull SyntaxInfo syntaxInfo, int maxLength) {
      myBuffer = buffer;
      myRawText = rawText;
      myMaxLength = maxLength;

      myDefaultBackgroundId = syntaxInfo.getDefaultBackground();
      myFontSize = syntaxInfo.getFontSize();
    }

    @Override
    public void handleText(int startOffset, int endOffset) throws Exception {
      myBuffer.append("\n");
      for (int i = startOffset; i < endOffset; i++) {
        char c = myRawText.charAt(i);
        if (c > 127) {
          // Escape non-ascii symbols.
          myBuffer.append(String.format("\\u%04d?", (int)c));
          continue;
        }

        switch (c) {
          case '\t':
            myBuffer.append(TAB);
            continue;
          case '\n':
            myBuffer.append(NEW_LINE);
            continue;
          case '\\':
          case '{':
          case '}':
            myBuffer.append('\\');
        }
        myBuffer.append(c);
      }
    }

    @Override
    public void handleBackground(int backgroundId) throws Exception {
      if (backgroundId == myDefaultBackgroundId) {
        myBuffer.append("\\plain"); // we cannot use \chcbpat with default background id, as it doesn't work in MS Word,
                                    // and we cannot use \chcbpat0 as it doesn't work in OpenOffice

        addFontSize(myBuffer, myFontSize);
        if (myFontNameId >= 0) {
          handleFont(myFontNameId);
        }
        if (myForegroundId >= 0) {
          handleForeground(myForegroundId);
        }
        if (myFontStyle >= 0) {
          handleStyle(myFontStyle);
        }
      }
      else {
        myBuffer.append("\\chcbpat").append(backgroundId);
      }
      myBuffer.append("\\cb").append(backgroundId);
      myBuffer.append('\n');
    }

    @Override
    public void handleForeground(int foregroundId) throws Exception {
      myBuffer.append("\\cf").append(foregroundId).append('\n');
      myForegroundId = foregroundId;
    }

    @Override
    public void handleFont(int fontNameId) throws Exception {
      myBuffer.append("\\f").append(fontNameId).append('\n');
      myFontNameId = fontNameId;
    }

    @Override
    public void handleStyle(int style) throws Exception {
      myBuffer.append(ITALIC);
      if ((style & Font.ITALIC) == 0) {
        myBuffer.append('0');
      }
      myBuffer.append(BOLD);
      if ((style & Font.BOLD) == 0) {
        myBuffer.append('0');
      }
      myBuffer.append('\n');
      myFontStyle = style;
    }

    @Override
    public boolean canHandleMore() {
      if (myBuffer.length() > myMaxLength) {
        myBuffer.append("... truncated ...");
        return false;
      }
      return true;
    }
  }
}
