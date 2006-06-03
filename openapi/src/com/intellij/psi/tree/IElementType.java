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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface for token types returned from lexical analysis and for types
 * of nodes in the AST tree. All used element types are added to a registry which
 * can be enumerated or accessed by index.
 *
 * @see com.intellij.lexer.Lexer#getTokenType()
 * @see com.intellij.lang.ASTNode#getElementType()
 */

public class IElementType {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.tree.IElementType");

  private static short ourCounter = 0;
  private static final short MAX_INDEXED_TYPES = 10000;
  private static final IElementType[] ourRegistry = new IElementType[MAX_INDEXED_TYPES];
  private final short myIndex;

  /**
   * Default enumeration predicate which matches all token types.
   *
   * @see #enumerate(com.intellij.psi.tree.IElementType.Predicate)
   */

  public final static Predicate TRUE = new Predicate() {
    public boolean matches(IElementType type) {
      return true;
    }
  };

  static int getAllocatedTypesCount() {
    return ourCounter;
  }

  public static final IElementType[] EMPTY_ARRAY = new IElementType[0];
  private final String myDebugName;
  private final @NotNull Language myLanguage;

  /**
   * Enumerates all registered token types which match the specified predicate.
   *
   * @param p the predicate which should be matched by the element types.
   * @return the array of matching element types.
   */

  public static IElementType[] enumerate(Predicate p) {
    List<IElementType> matches = new ArrayList<IElementType>();
    for (IElementType value : ourRegistry) {
      if (p.matches(value)) {
        matches.add(value);
      }
    }
    return matches.toArray(new IElementType[matches.size()]);
  }

  /**
   * Creates and registers a new element type for the specified language.
   *
   * @param debugName the name of the element type, used for debugging purposes.
   * @param language  the language with which the element type is associated.
   */

  public IElementType(@NotNull @NonNls String debugName, @Nullable Language language) {
    this(debugName, language, true);
  }

  protected IElementType(String debugName, Language language, final boolean register) {
    myDebugName = debugName;
    myLanguage = language == null ? Language.ANY : language;
    if (register) {
      myIndex = ourCounter++;
      LOG.assertTrue(ourCounter < MAX_INDEXED_TYPES, "Too many element types registered. Out of (short) range.");
      ourRegistry[myIndex] = this;
    }
    else {
      myIndex = -1;
    }
  }

  /**
   * Returns the language associated with the element type.
   *
   * @return the associated language.
   */

  public @NotNull Language getLanguage() {
    return myLanguage;
  }

  /**
   * Returns the index of the element type in the table of all registered element
   * types.
   *
   * @return the element type index.
   */

  public final short getIndex() {
    return myIndex;
  }

  public String toString() {
    return myDebugName;
  }

  /**
   * Returns the element type registered at the specified index.
   *
   * @param idx the indx for which the element type should be returned.
   * @return the element type at the specified index.
   */

  public static IElementType find(short idx) {
    return ourRegistry[idx];
  }

  /**
   * Predicate for matching element types.
   *
   * @see IElementType#enumerate(com.intellij.psi.tree.IElementType.Predicate)
   */

  public interface Predicate {
    boolean matches(IElementType type);
  }
}