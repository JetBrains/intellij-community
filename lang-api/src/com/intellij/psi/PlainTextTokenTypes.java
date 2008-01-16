package com.intellij.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.openapi.fileTypes.FileTypes;

public class PlainTextTokenTypes {
  public static final IElementType PLAIN_TEXT_FILE = new IFileElementType("PLAIN_TEXT_FILE", FileTypes.PLAIN_TEXT.getLanguage());
  public static final IElementType PLAIN_TEXT = new IElementType("PLAIN_TEXT", FileTypes.PLAIN_TEXT.getLanguage());

  private PlainTextTokenTypes() {
  }
}
