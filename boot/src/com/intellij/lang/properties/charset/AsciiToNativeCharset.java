package com.intellij.lang.properties.charset;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

/**
 * @author Alexey
 */

public class AsciiToNativeCharset extends Charset {
    public static final Charset INSTANCE = new AsciiToNativeCharset("ASCII_TO_NATIVE", null);

    public AsciiToNativeCharset(String canonicalName, String[] aliases) {
        super(canonicalName, aliases);
    }

    public boolean contains(Charset cs) {
        return false;
    }

    public CharsetDecoder newDecoder() {
        return new MyCharsetDecoder();
    }

    public CharsetEncoder newEncoder() {
        return new MyCharsetEncoder();
    }
}