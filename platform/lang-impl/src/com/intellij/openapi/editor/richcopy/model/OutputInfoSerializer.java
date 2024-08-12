// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.richcopy.model;

import com.intellij.util.io.CompactDataInput;
import com.intellij.util.io.CompactDataOutput;
import com.intellij.util.io.LZ4Decompressor;
import net.jpountz.lz4.LZ4DecompressorWithLength;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Not synchronized, stream implementations must be used from one thread at a time only
 */
public final class OutputInfoSerializer {
  private static final int TEXT_ID = 0;
  private static final int STYLE_ID = 1;
  private static final int FOREGROUND_ID = 2;
  private static final int BACKGROUND_ID = 3;
  private static final int FONT_ID = 4;

  public static final class OutputStream implements MarkupHandler {
    private final CompactDataOutput myOutputStream;
    private int myCurrentOffset;

    public OutputStream(java.io.OutputStream stream) {
      myOutputStream = new CompactDataOutput(stream);
    }

    @Override
    public void handleText(int startOffset, int endOffset) throws IOException {
      myOutputStream.write(TEXT_ID);
      myOutputStream.writeInt(startOffset - myCurrentOffset);
      myOutputStream.writeInt(endOffset - startOffset);
      myCurrentOffset = endOffset;
    }

    @Override
    public void handleForeground(int foregroundId) throws IOException {
      myOutputStream.write(FOREGROUND_ID);
      myOutputStream.writeInt(foregroundId);
    }

    @Override
    public void handleBackground(int backgroundId) throws IOException {
      myOutputStream.write(BACKGROUND_ID);
      myOutputStream.writeInt(backgroundId);
    }

    @Override
    public void handleFont(int fontNameId) throws IOException {
      myOutputStream.write(FONT_ID);
      myOutputStream.writeInt(fontNameId);
    }

    @Override
    public void handleStyle(int style) throws IOException {
      myOutputStream.write(STYLE_ID);
      myOutputStream.writeInt(style);
    }

    @Override
    public boolean canHandleMore() {
      return true;
    }
  }

  public static final class InputStream {
    private final CompactDataInput myInputStream;
    private int myCurrentOffset;

    public InputStream(byte[] stream) {
      myInputStream = new CompactDataInput(new ByteArrayInputStream(new LZ4DecompressorWithLength(LZ4Decompressor.INSTANCE).decompress(stream)));
    }

    public void read(MarkupHandler handler) throws Exception {
      int id = myInputStream.readByte();
      switch (id) {
        case TEXT_ID -> {
          int startOffset = myCurrentOffset + myInputStream.readInt();
          myCurrentOffset = startOffset;
          int endOffset = myCurrentOffset + myInputStream.readInt();
          myCurrentOffset = endOffset;
          handler.handleText(startOffset, endOffset);
        }
        case STYLE_ID -> handler.handleStyle(myInputStream.readInt());
        case FOREGROUND_ID -> handler.handleForeground(myInputStream.readInt());
        case BACKGROUND_ID -> handler.handleBackground(myInputStream.readInt());
        case FONT_ID -> handler.handleFont(myInputStream.readInt());
        default -> throw new IllegalStateException("Unknown tag id: " + id);
      }
    }
  }
}
