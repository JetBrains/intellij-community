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
import com.intellij.openapi.editor.richcopy.view.InputStreamTransferableData;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.io.UnsupportedEncodingException;

/**
 * @author Denis Zhdanov
 * @since 3/25/13 2:18 PM
 */
public class RtfCopyPasteProcessor extends BaseTextWithMarkupCopyPasteProcessor<InputStreamTransferableData> {
  @NotNull public static final DataFlavor FLAVOR = new DataFlavor("text/rtf;class=java.io.InputStream", "RTF text");

  private static final String CHARSET = "US-ASCII";

  @NotNull private static final String HEADER_PREFIX = "{\\rtf1\\ansi\\deff0";
  @NotNull private static final String HEADER_SUFFIX = "}";
  @NotNull private static final String TAB           = "\\tab\n";
  @NotNull private static final String NEW_LINE      = "\\line\n";
  @NotNull private static final String BOLD          = "\\b";
  @NotNull private static final String ITALIC        = "\\i";
  @NotNull private static final String PLAIN         = "\\plain\n";

  private int myBackgroundId;
  private int myForegroundId;
  private int myFontNameId;
  private ColorRegistry myColorRegistry;
  private FontNameRegistry myFontNameRegistry;

  public RtfCopyPasteProcessor(TextWithMarkupProcessor processor) {
    super(processor);
  }

  @Override
  protected void doInit() {
    myBackgroundId = -1;
    myForegroundId = -1;
    myFontNameId   = -1;
    myColorRegistry = new ColorRegistry();
    myFontNameRegistry = new FontNameRegistry();

    int defaultForegroundId = myColorRegistry.getId(myDefaultForeground);
    int defaultBackgroundId = myColorRegistry.getId(myDefaultBackground);

    myBuilder.append("\n\\s0\\box\\brdrhair\\brdrcf").append(defaultForegroundId).append("\\brsp317").append("\\cbpat").append(defaultBackgroundId);
    saveBackground(defaultBackgroundId);
  }

  @Override
  protected void doComplete() {
    StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(HEADER_PREFIX);

      // Color table.
      builder.append("{\\colortbl;");
      for (int id : myColorRegistry.getAllIds()) {
        Color color = myColorRegistry.dataById(id);
        builder.append(String.format("\\red%d\\green%d\\blue%d;", color.getRed(), color.getGreen(), color.getBlue()));
      }
      builder.append("}\n");

      // Font table.
      builder.append("{\\fonttbl");
      for (int id : myFontNameRegistry.getAllIds()) {
        String fontName = myFontNameRegistry.dataById(id);
        builder.append(String.format("{\\f%d %s;}", id, fontName));
      }
      builder.append("}\n");

      myBuilder.insert(0, builder);
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
    myBuilder.append("\\par");
    myBuilder.append(HEADER_SUFFIX);
  }

  @Override
  public void setFontFamily(String fontFamily) {
    int id = myFontNameRegistry.getId(fontFamily);
    saveFontName(id);
    myFontNameId = id;
  }

  @Override
  public void setFontStyle(int fontStyle) {
    // Reset formatting settings
    myBuilder.append(PLAIN);

    // Restore target formatting settings.
    if (myForegroundId >= 0) {
      saveForeground(myForegroundId);
    }
    if (myBackgroundId >= 0) {
      saveBackground(myBackgroundId);
    }
    if (myFontNameId >= 0) {
      saveFontName(myFontNameId);
    }
    saveFontSize(myFontSize);

    if ((fontStyle & Font.ITALIC) > 0) {
      myBuilder.append(ITALIC);
    }
    if ((fontStyle & Font.BOLD) > 0) {
      myBuilder.append(BOLD);
    }
  }

  @Override
  public void setForeground(Color foreground) {
    int id = myColorRegistry.getId(foreground);
    saveForeground(id);
    myForegroundId = id;
  }

  @Override
  public void setBackground(Color background) {
    int id = myColorRegistry.getId(background);
    saveBackground(id);
    myBackgroundId = id;
  }

  @Override
  public void addTextFragment(CharSequence charSequence, int startOffset, int endOffset) {
    myBuilder.append("\n");
    for (int i = startOffset; i < endOffset; i++) {
      char c = charSequence.charAt(i);
      if (c > 127) {
        // Escape non-ascii symbols.
        myBuilder.append(String.format("\\u%04d?", (int)c));
        continue;
      }

      switch (c) {
        case '\t':
          myBuilder.append(TAB);
          continue;
        case '\n':
          myBuilder.append(NEW_LINE);
          continue;
        case '\\':
        case '{':
        case '}':
          myBuilder.append('\\');
      }
      myBuilder.append(c);
    }
  }

  @Override
  protected InputStreamTransferableData createTransferable(@NotNull String data) {
    try {
      return new InputStreamTransferableData(data.getBytes(CHARSET), FLAVOR);
    }
    catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e); // shouldn't happen
    }
  }

  private void saveBackground(int id) {
    myBuilder.append("\\cb").append(id);
  }

  private void saveForeground(int id) {
    myBuilder.append("\\cf").append(id);
  }

  private void saveFontName(int id) {
    myBuilder.append("\\f").append(id);
  }

  private void saveFontSize(int size) {
    myBuilder.append("\\fs").append(size * 2);
  }
}
