// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import org.jetbrains.annotations.NotNull;

public final class PlainTextTokenTypes {
  public static final IElementType PLAIN_TEXT_FILE = new IFileElementType("PLAIN_TEXT_FILE", PlainTextLanguage.INSTANCE) {
    @Override
    public ASTNode parseContents(@NotNull ASTNode chameleon) {
      return ASTFactory.leaf(PLAIN_TEXT, chameleon.getChars());
    }
  };

  public static final IElementType PLAIN_TEXT = new IElementType("PLAIN_TEXT", PlainTextLanguage.INSTANCE);

  private PlainTextTokenTypes() {
  }
}
