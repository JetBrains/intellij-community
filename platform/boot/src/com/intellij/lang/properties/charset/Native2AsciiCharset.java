// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.charset;

import java.nio.charset.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class Native2AsciiCharset extends Charset {
  @SuppressWarnings("SSBasedInspection")
  private static final String[] ALIASES = new String[0];
  private final Charset myBaseCharset;
  private static final String NAME_PREFIX = "NATIVE_TO_ASCII_";
  private static final String DEFAULT_ENCODING_NAME = "ISO-8859-1";

  private Native2AsciiCharset(String canonicalName) {
    super(canonicalName, ALIASES);
    Charset baseCharset = null;
    try {
      String baseCharsetName = canonicalName.substring(NAME_PREFIX.length());
      baseCharset = Charset.forName(baseCharsetName);
    }
    catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
      //ignore
    }
    myBaseCharset = baseCharset == null ? Charset.forName(DEFAULT_ENCODING_NAME) : baseCharset;
  }

  @Override
  public String displayName() {
    return getBaseCharset().displayName();
  }

  @Override
  public boolean contains(Charset cs) {
    return false;
  }

  @Override
  public CharsetDecoder newDecoder() {
    return new Native2AsciiCharsetDecoder(this);
  }

  @Override
  public CharsetEncoder newEncoder() {
    return new Native2AsciiCharsetEncoder(this);
  }

  Charset getBaseCharset() {
    return myBaseCharset;
  }
  public static String makeNative2AsciiEncodingName(String baseCharsetName) {
    if (baseCharsetName == null) baseCharsetName = DEFAULT_ENCODING_NAME;
    return NAME_PREFIX + baseCharsetName;
  }

  public static Charset forName(String charsetName) {
    if (charsetName.startsWith(NAME_PREFIX)) {
      Native2AsciiCharset cached = cache.get(charsetName);
      if (cached == null) {
        cached = new Native2AsciiCharset(charsetName);
        Native2AsciiCharset prev = cache.putIfAbsent(charsetName, cached);
        if (prev != null) cached = prev;
      }
      return cached;
    }
    return null;
  }
  public static Charset wrap(Charset baseCharset) {
    return forName(NAME_PREFIX + baseCharset.name());
  }

  public static Charset nativeToBaseCharset(Charset charset) {
    if (charset instanceof Native2AsciiCharset) {
      return ((Native2AsciiCharset)charset).getBaseCharset();
    }
    return charset;
  }

  private static final ConcurrentMap<String, Native2AsciiCharset> cache = new ConcurrentHashMap<>();
}