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

import gnu.trove.TIntObjectHashMap;

import java.util.ArrayList;
import java.util.List;

import com.intellij.openapi.diagnostic.Logger;

public class IElementType {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.tree.IElementType");

  private static int ourCounter = 0;
  private static final TIntObjectHashMap ourRegistry = new TIntObjectHashMap();
  private final short myIndex;

  public final static Predicate TRUE = new Predicate() {
    public boolean matches(IElementType type) {
      return true;
    }
  };
  public static final IElementType[] EMPTY_ARRAY = new IElementType[0];
  private String myDebugName;

  public static IElementType[] enumerate(Predicate p) {
    List matches = new ArrayList();
    Object[] values = ourRegistry.getValues();
    for (int i = 0; i < values.length; i++) {
      IElementType value = (IElementType)values[i];
      if (p.matches(value)) {
        matches.add(value);                                                       
      }
    }
    return (IElementType[])matches.toArray(new IElementType[matches.size()]);
  }

  public IElementType(String debugName) {
    myDebugName = debugName;
    myIndex = (short) ourCounter++;
    LOG.assertTrue(ourCounter < Short.MAX_VALUE, "Too many element types registered. Out of (short) range.");
    ourRegistry.put(myIndex, this);
  }

  public final short getIndex() {
    return myIndex;
  }

  public String toString() {
    return myDebugName;
  }

  public static IElementType find(short idx) {
    return (IElementType)ourRegistry.get(idx);
  }

  public interface Predicate {
    boolean matches(IElementType type);
  }
}