/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.lang.properties.charset;

import java.nio.charset.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Alexey
 */

public class Native2AsciiCharset extends Charset {
  private static final String[] ALIASES = new String[0];
  private final Charset myBaseCharset;
  @SuppressWarnings({"HardCodedStringLiteral"}) private static final String NAME_PREFIX = "NATIVE_TO_ASCII_";
  @SuppressWarnings({"HardCodedStringLiteral"}) private static final String DEFAULT_ENCODING_NAME = "ISO-8859-1";

  private Native2AsciiCharset(String canonicalName) {
    super(canonicalName, ALIASES);
    String baseCharsetName = canonicalName.substring(NAME_PREFIX.length());
    Charset baseCharset = null;
    try {
      baseCharset = Charset.forName(baseCharsetName);
    }
    catch (IllegalCharsetNameException e) {
      //ignore
    }
    catch(UnsupportedCharsetException e){
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

  public Charset getBaseCharset() {
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