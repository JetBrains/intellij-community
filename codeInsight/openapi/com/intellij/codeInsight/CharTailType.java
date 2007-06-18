/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight;

import com.intellij.openapi.editor.Editor;
import com.intellij.codeInsight.TailType;

/**
 * @author peter
*/
public class CharTailType extends TailType {
  final char myChar;

  public CharTailType(final char aChar) {
    myChar = aChar;
  }

  public int processTail(final Editor editor, final int tailOffset) {
    return insertChar(editor, tailOffset, myChar);
  }
}
