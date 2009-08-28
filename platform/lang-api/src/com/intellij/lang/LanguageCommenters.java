package com.intellij.lang;

/**
 * @author yole
 */
public class LanguageCommenters extends LanguageExtension<Commenter> {
  public static final LanguageCommenters INSTANCE = new LanguageCommenters();

  private LanguageCommenters() {
    super("com.intellij.lang.commenter");
  }

}