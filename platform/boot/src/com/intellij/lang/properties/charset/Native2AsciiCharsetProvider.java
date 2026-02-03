// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.charset;

import org.jetbrains.annotations.ApiStatus.Internal;

import java.nio.charset.Charset;
import java.nio.charset.spi.CharsetProvider;
import java.util.Collections;
import java.util.Iterator;

@Internal
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