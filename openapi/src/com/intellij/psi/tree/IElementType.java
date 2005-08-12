/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.tree;

import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

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

  public IElementType(@NotNull @NonNls String debugName, @NotNull Language language) {
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