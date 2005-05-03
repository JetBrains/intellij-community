package com.intellij.lang.properties.charset;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

/**
 * @author Alexey
 */

public class Native2AsciiCharset extends Charset {
  public static final Charset INSTANCE = new Native2AsciiCharset("NATIVE_TO_ASCII", null);

  public Native2AsciiCharset(String canonicalName, String[] aliases) {
    super(canonicalName, aliases);
  }

  public boolean contains(Charset cs) {
    return false;
  }

  public CharsetDecoder newDecoder() {
    return new Native2AsciiCharsetDecoder();
  }

  public CharsetEncoder newEncoder() {
    return new Native2AsciiCharsetEncoder();
  }
}