package com.intellij.lang.properties.charset;

import java.nio.charset.spi.CharsetProvider;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Collections;

/**
 * @author Alexey
 */

public class MyCharsetProvider extends CharsetProvider {
    public MyCharsetProvider() {
    }

    public Charset charsetForName(String charsetName) {
        if (AsciiToNativeCharset.INSTANCE.name().equals(charsetName)) {
            return AsciiToNativeCharset.INSTANCE;
        }
        return null;
    }

    public Iterator charsets() {
        return Collections.singleton(AsciiToNativeCharset.INSTANCE).iterator();
    }
}