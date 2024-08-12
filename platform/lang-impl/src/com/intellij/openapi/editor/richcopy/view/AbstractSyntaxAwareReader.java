// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.richcopy.view;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.richcopy.model.SyntaxInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

public abstract class AbstractSyntaxAwareReader extends Reader {

  private static final Logger LOG = Logger.getInstance(AbstractSyntaxAwareReader.class);

  protected String myRawText;
  protected final @NotNull SyntaxInfo mySyntaxInfo;

  private transient @Nullable Reader myDelegate;

  public AbstractSyntaxAwareReader(@NotNull SyntaxInfo syntaxInfo) {
    mySyntaxInfo = syntaxInfo;
  }
  
  @Override
  public int read() throws IOException {
    return getDelegate().read();
  }

  @Override
  public int read(char @NotNull [] cbuf, int off, int len) throws IOException {
    return getDelegate().read(cbuf, off, len);
  }

  @Override
  public void close() throws IOException {
    myDelegate = null;
  }

  public void setRawText(String rawText) {
    myRawText = rawText;
  }
  
  private @NotNull Reader getDelegate() {
    if (myDelegate != null) {
      return myDelegate;
    }

    myDelegate = new StringReader(getBuffer().toString());
    return myDelegate;
  }

  public final @NotNull CharSequence getBuffer() {
    final StringBuilder buffer = new StringBuilder();
    try {
      build(buffer, Registry.intValue("editor.richcopy.max.size.megabytes") * FileUtilRt.MEGABYTE);
    }
    catch (Exception e) {
      LOG.error(e);
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Resulting text: \n" + buffer);
    }
    return buffer;
  }

  protected abstract void build(@NotNull StringBuilder holder, int maxLength);
}
