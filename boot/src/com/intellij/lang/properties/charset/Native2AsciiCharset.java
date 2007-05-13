package com.intellij.lang.properties.charset;

import java.nio.charset.*;

/**
 * @author Alexey
 */

public class Native2AsciiCharset extends Charset {
  private final Charset myBaseCharset;
  @SuppressWarnings({"HardCodedStringLiteral"}) private static final String NAME_PREFIX = "NATIVE_TO_ASCII_";
  @SuppressWarnings({"HardCodedStringLiteral"}) private static final String DEAFULT_ENCODING_NAME = "ISO-8859-1";

  private Native2AsciiCharset(String canonicalName) {
    super(canonicalName, null);
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
    myBaseCharset = baseCharset == null ? Charset.forName(DEAFULT_ENCODING_NAME) : baseCharset;
  }

  public boolean contains(Charset cs) {
    return false;
  }

  public CharsetDecoder newDecoder() {
    return new Native2AsciiCharsetDecoder(this);
  }

  public CharsetEncoder newEncoder() {
    return new Native2AsciiCharsetEncoder(this);
  }

  public Charset getBaseCharset() {
    return myBaseCharset;
  }
  public static String makeNative2AsciiEncodingName(String baseCharsetName) {
    if (baseCharsetName == null) baseCharsetName = DEAFULT_ENCODING_NAME;
    return NAME_PREFIX + baseCharsetName;
  }
  public static Charset forName(String charsetName) {
    if (charsetName.startsWith(NAME_PREFIX)) {
      return new Native2AsciiCharset(charsetName);
    }
    return null;
  }
}