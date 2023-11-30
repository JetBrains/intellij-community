package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.javadoc.JavadocHelper;

/**
 * Serves as a facade for javadoc smart completion.
 * <p/>
 * Thread-safe.
 */
public class JavadocFixer extends AbstractBasicJavadocFixer {

  public JavadocFixer() {
    super( new JavadocHelper());
  }
}
