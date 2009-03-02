package com.intellij.psi;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;

public class PlainTextTokenTypes {
  public static final IElementType PLAIN_TEXT_FILE = new IFileElementType("PLAIN_TEXT_FILE", FileTypes.PLAIN_TEXT.getLanguage()) {
    @Override
    public ASTNode parseContents(ASTNode chameleon) {
      return ASTFactory.leaf(PLAIN_TEXT, chameleon.getChars());
    }
  };

  public static final IElementType PLAIN_TEXT = new IElementType("PLAIN_TEXT", FileTypes.PLAIN_TEXT.getLanguage());

  private PlainTextTokenTypes() {
  }
}
