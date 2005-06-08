package com.intellij.psi.tree;

import com.intellij.lang.Language;

public class IFileElementType extends IChameleonElementType {
  public IFileElementType(final Language language) {
    super("FILE", language);
  }

  public IFileElementType(String debugName, Language language) {
    super(debugName, language);
  }
}
