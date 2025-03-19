// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.richcopy.view;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.richcopy.model.SyntaxInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

abstract class AbstractSyntaxAwareInputStreamTransferableData extends InputStream implements RawTextWithMarkup {
  private static final Logger LOG = Logger.getInstance(AbstractSyntaxAwareInputStreamTransferableData.class);

  String myRawText;
  final @NotNull SyntaxInfo mySyntaxInfo;
  private final @NotNull DataFlavor myDataFlavor;

  private transient @Nullable InputStream myDelegate;

  AbstractSyntaxAwareInputStreamTransferableData(@NotNull SyntaxInfo syntaxInfo, @NotNull DataFlavor flavor) {
    mySyntaxInfo = syntaxInfo;
    myDataFlavor = flavor;
  }

  @Override
  public @Nullable DataFlavor getFlavor() {
    return myDataFlavor;
  }

  @Override
  public int read() throws IOException {
    return getDelegate().read();
  }

  @Override
  public int read(byte @NotNull [] b, int off, int len) throws IOException {
    return getDelegate().read(b, off, len);
  }

  @Override
  public void close() throws IOException {
    myDelegate = null;
  }

  @Override
  public void setRawText(String rawText) {
    myRawText = rawText;
  }

  private @NotNull InputStream getDelegate() {
    if (myDelegate != null) {
      return myDelegate;
    }

    int maxLength = Registry.intValue("editor.richcopy.max.size.megabytes") * FileUtilRt.MEGABYTE;
    final StringBuilder buffer = new StringBuilder();
    try {
      build(buffer, maxLength);
    }
    catch (Exception e) {
      LOG.error(e);
    }
    String s = buffer.toString();
    if (LOG.isDebugEnabled()) {
      LOG.debug("Resulting text: \n" + s);
    }
    try {
      myDelegate = new ByteArrayInputStream(s.getBytes(getCharset()));
    }
    catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
    return myDelegate;
  }
  
  protected abstract void build(@NotNull StringBuilder holder, int maxLength);

  protected abstract @NotNull String getCharset();

  @Override
  public synchronized void mark(int readlimit) {
    getDelegate().mark(readlimit);
  }

  @Override
  public synchronized void reset() throws IOException {
    getDelegate().reset();
  }

  @Override
  public boolean markSupported() {
    return getDelegate().markSupported();
  }
}
