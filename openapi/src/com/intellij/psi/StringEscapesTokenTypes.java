/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.psi.tree.IElementType;


/**
 * @author max
 */
public interface StringEscapesTokenTypes {
  IElementType VALID_STRING_ESCAPE_TOKEN = new IElementType("VALID_STRING_ESCAPE_TOKEN");
  IElementType INVALID_STRING_ESCAPE_TOKEN = new IElementType("INVALID_STRING_ESCAPE_TOKEN");
}
