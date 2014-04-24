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
    header(mySyntaxInfo, holder, new Runnable() {
      @Override
      public void run() {
        rectangularBackground(mySyntaxInfo, holder, new Runnable() {
          @Override
          public void run() {
            content(mySyntaxInfo, holder, myRawText, maxLength);
          }
        });
      }
    });
  }

  @NotNull
  @Override
  protected String getCharset() {
    return "US-ASCII";
  }

  private static void header(@NotNull SyntaxInfo syntaxInfo, @NotNull StringBuilder buffer, @NotNull Runnable next) {
    buffer.append(HEADER_PREFIX);

    // Color table.
    buffer.append("{\\colortbl;");
    ColorRegistry colorRegistry = syntaxInfo.getColorRegistry();
    for (int id : colorRegistry.getAllIds()) {
      Color color = colorRegistry.dataById(id);
      buffer.append(String.format("\\red%d\\green%d\\blue%d;", color.getRed(), color.getGreen(), color.getBlue()));
    }
    buffer.append("}\n");
    
    // Font table.
    buffer.append("{\\fonttbl");
    FontNameRegistry fontNameRegistry = syntaxInfo.getFontNameRegistry();
    for (int id : fontNameRegistry.getAllIds()) {
      String fontName = fontNameRegistry.dataById(id);
      buffer.append(String.format("{\\f%d %s;}", id, fontName));
    }
    buffer.append("}\n");

    next.run();
    buffer.append(HEADER_SUFFIX);
  }

  private static void rectangularBackground(@NotNull SyntaxInfo syntaxInfo, @NotNull StringBuilder buffer, @NotNull Runnable next) {
    buffer.append("\n\\s0\\box").append("\\cbpat").append(syntaxInfo.getDefaultBackground());
    saveBackground(buffer, syntaxInfo.getDefaultBackground());
    buffer.append("\\fs").append(syntaxInfo.getSingleFontSize() * 2).append('\n');
    next.run();
    buffer.append("\\par");
  }

  private static void content(@NotNull SyntaxInfo syntaxInfo, @NotNull StringBuilder buffer, @NotNull String rawText, int maxLength) {
    MyVisitor visitor = new MyVisitor(buffer, rawText);
    SyntaxInfo.MarkupIterator it = syntaxInfo.new MarkupIterator();
    try {
      while(it.hasNext()) {
        it.processNext(visitor);
        if (buffer.length() > maxLength) {
          buffer.append("... truncated ...");
          break;
        }
      }
    }
    finally {
      it.dispose();
    }
  }

  private static void saveBackground(@NotNull StringBuilder buffer, int id) {
    buffer.append("\\cb").append(id);
  }

  private static class MyVisitor implements MarkupHandler {

    @NotNull private final StringBuilder myBuffer;
    @NotNull private final String        myRawText;

    MyVisitor(@NotNull StringBuilder buffer, @NotNull String rawText) {
      myBuffer = buffer;
      myRawText = rawText;
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
    public void handleForeground(int foregroundId) throws Exception {
      myBuffer.append("\\cf").append(foregroundId);
    }

    @Override
    public void handleBackground(int backgroundId) throws Exception {
      saveBackground(myBuffer, backgroundId);
    }

    @Override
    public void handleFont(int fontNameId) throws Exception {
      myBuffer.append("\\f").append(fontNameId);
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
    }
  }
}
