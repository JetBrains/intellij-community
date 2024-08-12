// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.richcopy.model;

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.io.LZ4Compressor;
import net.jpountz.lz4.LZ4CompressorWithLength;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.IOException;

public final class SyntaxInfo {
  private final int myOutputInfoCount;
  private final byte[] myOutputInfosSerialized;
  private final @NotNull ColorRegistry myColorRegistry;
  private final @NotNull FontNameRegistry myFontNameRegistry;

  private final int myDefaultForeground;
  private final int myDefaultBackground;
  private final float myFontSize;

  private SyntaxInfo(int outputInfoCount,
                     byte[] outputInfosSerialized,
                     int defaultForeground,
                     int defaultBackground,
                     float fontSize,
                     @NotNull FontNameRegistry fontNameRegistry,
                     @NotNull ColorRegistry colorRegistry) {
    myOutputInfoCount = outputInfoCount;
    myOutputInfosSerialized = outputInfosSerialized;
    myDefaultForeground = defaultForeground;
    myDefaultBackground = defaultBackground;
    myFontSize = fontSize;
    myFontNameRegistry = fontNameRegistry;
    myColorRegistry = colorRegistry;
  }

  public @NotNull ColorRegistry getColorRegistry() {
    return myColorRegistry;
  }

  public @NotNull FontNameRegistry getFontNameRegistry() {
    return myFontNameRegistry;
  }

  public int getDefaultForeground() {
    return myDefaultForeground;
  }

  public int getDefaultBackground() {
    return myDefaultBackground;
  }

  public float getFontSize() {
    return myFontSize;
  }

  public void processOutputInfo(MarkupHandler handler) {
    MarkupIterator it = new MarkupIterator();
    while (it.hasNext()) {
      it.processNext(handler);
      if (!handler.canHandleMore()) {
        break;
      }
    }
  }

  @Override
  public String toString() {
    final StringBuilder b = new StringBuilder();
    b.append("default colors: foreground=").append(myDefaultForeground).append(", background=").append(myDefaultBackground)
      .append("; output infos: ");
    boolean first = true;
    MarkupIterator it = new MarkupIterator();
    while (it.hasNext()) {
      if (first) {
        b.append(',');
      }
      it.processNext(new MarkupHandler() {
        @Override
        public void handleText(int startOffset, int endOffset) {
          b.append("text(").append(startOffset).append(",").append(endOffset).append(")");
        }

        @Override
        public void handleForeground(int foregroundId) {
          b.append("foreground(").append(foregroundId).append(")");
        }

        @Override
        public void handleBackground(int backgroundId) {
          b.append("background(").append(backgroundId).append(")");
        }

        @Override
        public void handleFont(int fontNameId) {
          b.append("font(").append(fontNameId).append(")");
        }

        @Override
        public void handleStyle(int style) {
          b.append("style(").append(style).append(")");
        }

        @Override
        public boolean canHandleMore() {
          return true;
        }
      });
      first = false;
    }
    return b.toString();
  }

  public static final class Builder {
    private final ColorRegistry myColorRegistry = new ColorRegistry();
    private final FontNameRegistry myFontNameRegistry = new FontNameRegistry();
    private final int myDefaultForeground;
    private final int myDefaultBackground;
    private final float myFontSize;
    private final BufferExposingByteArrayOutputStream myStream = new BufferExposingByteArrayOutputStream();
    private final OutputInfoSerializer.OutputStream myOutputInfoStream;
    private int myOutputInfoCount;

    public Builder(Color defaultForeground, Color defaultBackground, float fontSize) {
      myDefaultForeground = myColorRegistry.getId(defaultForeground);
      myDefaultBackground = myColorRegistry.getId(defaultBackground);
      myFontSize = fontSize;
      myOutputInfoStream = new OutputInfoSerializer.OutputStream(myStream);
    }

    public void addFontStyle(int fontStyle) {
      try {
        myOutputInfoStream.handleStyle(fontStyle);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      myOutputInfoCount++;
    }

    public void addFontFamilyName(String fontFamilyName) {
      try {
        myOutputInfoStream.handleFont(myFontNameRegistry.getId(fontFamilyName));
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      myOutputInfoCount++;
    }

    public void addForeground(Color foreground) {
      try {
        myOutputInfoStream.handleForeground(myColorRegistry.getId(foreground));
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      myOutputInfoCount++;
    }

    public void addBackground(Color background) {
      try {
        myOutputInfoStream.handleBackground(myColorRegistry.getId(background));
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      myOutputInfoCount++;
    }

    public void addText(int startOffset, int endOffset) {
      try {
        myOutputInfoStream.handleText(startOffset, endOffset);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      myOutputInfoCount++;
    }

    public SyntaxInfo build() {
      myColorRegistry.seal();
      myFontNameRegistry.seal();
      byte[] compressed =
        new LZ4CompressorWithLength(LZ4Compressor.INSTANCE).compress(myStream.getInternalBuffer(), 0,
                                                             myStream.size());
      return new SyntaxInfo(myOutputInfoCount, compressed, myDefaultForeground, myDefaultBackground, myFontSize, myFontNameRegistry,
                            myColorRegistry);
    }
  }

  public final class MarkupIterator {
    private int pos;
    private final OutputInfoSerializer.InputStream myOutputInfoStream;

    MarkupIterator() {
      myOutputInfoStream = new OutputInfoSerializer.InputStream(myOutputInfosSerialized);
    }

    public boolean hasNext() {
      return pos < myOutputInfoCount;
    }

    public void processNext(MarkupHandler handler) {
      if (!hasNext()) {
        throw new IllegalStateException();
      }
      pos++;
      try {
        myOutputInfoStream.read(handler);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
