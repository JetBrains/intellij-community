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
package com.intellij.psi.tree;

import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class IElementType {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.tree.IElementType");

  private static int ourCounter = 0;
  private static final TIntObjectHashMap<IElementType> ourRegistry = new TIntObjectHashMap<IElementType>();
  private final short myIndex;

  public final static Predicate TRUE = new Predicate() {
    public boolean matches(IElementType type) {
      return true;
    }
  };
  public static final IElementType[] EMPTY_ARRAY = new IElementType[0];
  private final String myDebugName;
  private final @NotNull Language myLanguage;

  public static IElementType[] enumerate(Predicate p) {
    List<IElementType> matches = new ArrayList<IElementType>();
    Object[] values = ourRegistry.getValues();
    for (Object value1 : values) {
      IElementType value = (IElementType)value1;
      if (p.matches(value)) {
        matches.add(value);
      }
    }
    return matches.toArray(new IElementType[matches.size()]);
  }

  public IElementType(@NotNull String debugName, @NotNull Language language) {
    this(debugName, language, true);
  }

  protected IElementType(String debugName, Language language, final boolean register) {
    myDebugName = debugName;
    myLanguage = language == null ? Language.ANY : language;
    if (register) {
      myIndex = (short) ourCounter++;
      LOG.assertTrue(ourCounter < Short.MAX_VALUE, "Too many element types registered. Out of (short) range.");
      ourRegistry.put(myIndex, this);
    }
    else {
      myIndex = -1;
    }
  }

  public @NotNull Language getLanguage() {
    return myLanguage;
  }

  public final short getIndex() {
    return myIndex;
  }

  public String toString() {
    return myDebugName;
  }

  public static IElementType find(short idx) {
    return ourRegistry.get(idx);
  }

  public interface Predicate {
    boolean matches(IElementType type);
  }
}