/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

import java.util.Map;

public abstract class FileHighlighterBase implements FileHighlighter {
  private static final TextAttributesKey[] EMPTY = new TextAttributesKey[0];

  protected static TextAttributesKey[] pack(TextAttributesKey key) {
    if (key == null) return EMPTY;
    return new TextAttributesKey[] {key};
  }

  protected static TextAttributesKey[] pack(TextAttributesKey key1, TextAttributesKey key2) {
    if (key1 == null) return pack(key2);
    if (key2 == null) return pack(key1);
    return new TextAttributesKey[] {key1, key2};
  }

  protected static TextAttributesKey[] pack(TextAttributesKey[] base, TextAttributesKey key) {
    if (key == null) return base;
    TextAttributesKey[] result = new TextAttributesKey[base.length + 1];
    System.arraycopy(base, 0, result, 0, base.length);
    result[base.length] = key;
    return result;
  }
  protected static TextAttributesKey[] pack(TextAttributesKey key, TextAttributesKey[] base) {
    if (key == null) return base;
    TextAttributesKey[] result = new TextAttributesKey[base.length + 1];
    System.arraycopy(base, 0, result, 1, base.length);
    result[0] = key;
    return result;
  }

  protected static void fillMap(Map<IElementType, TextAttributesKey> map, TokenSet keys, TextAttributesKey value) {
    IElementType[] types = keys.getTypes();
    for (int i = 0; i < types.length; i++) {
      map.put(types[i], value);
    }
  }

  protected static TextAttributesKey[] pack(TextAttributesKey[] base, TextAttributesKey t1, TextAttributesKey t2) {
    int add = 0;
    if (t1 != null) add++;
    if (t2 != null) add++;
    if (add == 0) return base;
    TextAttributesKey[] result = new TextAttributesKey[base.length + add];
    add = base.length;
    System.arraycopy(base, 0, result, 0, base.length);
    if (t1 != null) result[add++] = t1;
    if (t2 != null) result[add++] = t2;
    return result;
  }
}