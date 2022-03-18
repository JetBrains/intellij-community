// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.charset;

import com.intellij.lang.properties.charset.Native2AsciiCharset;

import java.nio.charset.Charset;
import java.nio.charset.spi.CharsetProvider;
import java.util.Collections;
import java.util.Iterator;

public final class Native2AsciiCharsetProvider extends CharsetProvider {
  public Native2AsciiCharsetProvider() {
  }

  @Override
  public Charset charsetForName(String charsetName) {
    return Native2AsciiCharset.forName(charsetName);
  }

  @Override
  public Iterator<Charset> charsets() {
    return Collections.emptyIterator();
  }
}