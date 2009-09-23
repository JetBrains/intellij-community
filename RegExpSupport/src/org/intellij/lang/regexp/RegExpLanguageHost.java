package org.intellij.lang.regexp;

/**
 * @author yole
 */
public interface RegExpLanguageHost {
  boolean characterNeedsEscaping(char c);
  boolean supportsPerl5EmbeddedComments();
}
