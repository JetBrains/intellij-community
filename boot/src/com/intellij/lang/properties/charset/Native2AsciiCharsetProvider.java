package com.intellij.lang.properties.charset;

import java.nio.charset.spi.CharsetProvider;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Collections;

/**
 * @author Alexey
 */
@SuppressWarnings({"UnusedDeclaration"})
public class Native2AsciiCharsetProvider extends CharsetProvider {
  public Native2AsciiCharsetProvider() {
  }

  public Charset charsetForName(String charsetName) {
    return Native2AsciiCharset.forName(charsetName);
  }

  public Iterator<Charset> charsets() {
    return Collections.<Charset>emptyList().iterator();
  }
}