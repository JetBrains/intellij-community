// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.richcopy.model;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author Denis Zhdanov
 */
public final class SyntaxInfo {
  private final int myOutputInfoCount;
  private final byte[] myOutputInfosSerialized;
  @NotNull private final ColorRegistry    myColorRegistry;
  @NotNull private final FontNameRegistry myFontNameRegistry;

  private final int myDefaultForeground;
  private final int myDefaultBackground;
  private final float myFontSize;

  private SyntaxInfo(int outputInfoCount,
                     byte[] outputInfosSerialized,
                    int defaultForeground,
                    int defaultBackground,
                    float fontSize,
                    @NotNull FontNameRegistry fontNameRegistry,
                    @NotNull ColorRegistry colorRegistry)
  {
    myOutputInfoCount = outputInfoCount;
    myOutputInfosSerialized = outputInfosSerialized;
    myDefaultForeground = defaultForeground;
    myDefaultBackground = defaultBackground;
    myFontSize = fontSize;
    myFontNameRegistry = fontNameRegistry;
    myColorRegistry = colorRegistry;
  }

  @NotNull
  public ColorRegistry getColorRegistry() {
    return myColorRegistry;
  }

  @NotNull
  public FontNameRegistry getFontNameRegistry() {
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
    try {
      while(it.hasNext()) {
        it.processNext(handler);
        if (!handler.canHandleMore()) {
          break;
        }
      }
    }
    finally {
      it.dispose();
    }
  }

  @Override
  public String toString() {
    final StringBuilder b = new StringBuilder();
    b.append("default colors: foreground=").append(myDefaultForeground).append(", background=").append(myDefaultBackground).append("; output infos: ");
    boolean first = true;
    MarkupIterator it = new MarkupIterator();
    try {
      while(it.hasNext()) {
        if (first) {
          b.append(',');
        }
        it.processNext(new MarkupHandler() {
          @Override
          public void handleText(int startOffset, int endOffset) throws Exception {
            b.append("text(").append(startOffset).append(",").append(endOffset).append(")");
          }

          @Override
          public void handleForeground(int foregroundId) throws Exception {
            b.append("foreground(").append(foregroundId).append(")");
          }

          @Override
          public void handleBackground(int backgroundId) throws Exception {
            b.append("background(").append(backgroundId).append(")");
          }

          @Override
          public void handleFont(int fontNameId) throws Exception {
            b.append("font(").append(fontNameId).append(")");
          }

          @Override
          public void handleStyle(int style) throws Exception {
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
    finally {
      it.dispose();
    }
  }

  public static class Builder {
    private final ColorRegistry myColorRegistry = new ColorRegistry();
    private final FontNameRegistry myFontNameRegistry = new FontNameRegistry();
    private final int myDefaultForeground;
    private final int myDefaultBackground;
    private final float myFontSize;
    private final ByteArrayOutputStream myStream = new ByteArrayOutputStream();
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
      try {
        myOutputInfoStream.close();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      return new SyntaxInfo(myOutputInfoCount, myStream.toByteArray(), myDefaultForeground, myDefaultBackground, myFontSize, myFontNameRegistry, myColorRegistry);
    }
  }

  public class MarkupIterator {
    private int pos;
    private final OutputInfoSerializer.InputStream myOutputInfoStream;

    public MarkupIterator() {
      myOutputInfoStream = new OutputInfoSerializer.InputStream(new ByteArrayInputStream(myOutputInfosSerialized));
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

    public void dispose() {
      try {
        myOutputInfoStream.close();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
