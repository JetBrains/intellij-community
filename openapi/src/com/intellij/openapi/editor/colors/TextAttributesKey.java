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
package com.intellij.openapi.editor.colors;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.TextAttributes;

import java.util.HashMap;
import java.util.Map;

public final class TextAttributesKey implements Comparable<TextAttributesKey> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.colors.TextAttributesKey");
  private static final TextAttributes NULL_ATTRIBUTES = new TextAttributes();

  private final String myExternalName;
  private TextAttributes myDefaultAttributes = NULL_ATTRIBUTES;
  private static Map<String, TextAttributesKey> ourRegistry = new HashMap<String, TextAttributesKey>();

  private TextAttributesKey(String externalName) {
    myExternalName = externalName;
    if (ourRegistry.containsKey(myExternalName)) {
      LOG.error("Key " + myExternalName + " already registered.");
    }
    else {
      ourRegistry.put(myExternalName, this);
    }
  }

  public static TextAttributesKey find(String externalName) {
    TextAttributesKey key = ourRegistry.get(externalName);
    return key != null ? key : new TextAttributesKey(externalName);
  }

  public String toString() {
    return myExternalName;
  }

  public String getExternalName() {
    return myExternalName;
  }

  public int compareTo(TextAttributesKey key) {
    return myExternalName.compareTo(key.myExternalName);
  }

  public TextAttributes getDefaultAttributes() {
    if (myDefaultAttributes == NULL_ATTRIBUTES) {
      myDefaultAttributes = null;
      EditorColorsManager manager = EditorColorsManager.getInstance();

      if (manager != null) { // Can be null in test mode
        myDefaultAttributes = manager.getGlobalScheme().getAttributes(this);
      }
    }

    return myDefaultAttributes;
  }

  public static TextAttributesKey createTextAttributesKey(String externalName, TextAttributes defaultAttributes) {
    TextAttributesKey key = ourRegistry.get(externalName);
    if (key == null) {
      key = find(externalName);
    }
    else {
      LOG.assertTrue(key.getDefaultAttributes() == null, "default attributes already assigned");
    }
    key.myDefaultAttributes = defaultAttributes;
    return key;
  }

  public static TextAttributesKey createTextAttributesKey(String externalName) {
    return find(externalName);
  }
}