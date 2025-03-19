package com.siyeh.igtest.internationalization.unnecessary_unicode_escape;

/// We should not get any warning in this file for brackets
/// As demonstrated by [String#copyValueOf(char\d[\])]
class UnnecessaryUnicodeEscape {}