package com.intellij.lang.properties.charset;

import java.nio.charset.spi.CharsetProvider;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Collections;

/**
 * @author Alexey
 */

public class Native2AsciiCharsetProvider extends CharsetProvider {
    public Native2AsciiCharsetProvider() {
    }

    public Charset charsetForName(String charsetName) {
        if (Native2AsciiCharset.INSTANCE.name().equals(charsetName)) {
            return Native2AsciiCharset.INSTANCE;
        }
        return null;
    }

    public Iterator charsets() {
        return Collections.singleton(Native2AsciiCharset.INSTANCE).iterator();
    }
}