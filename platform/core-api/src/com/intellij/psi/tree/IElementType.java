/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

  public static final IElementType[] EMPTY_ARRAY = new IElementType[0];
  public static final ArrayFactory<IElementType> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new IElementType[count];

  /**
   * Default enumeration predicate which matches all token types.
   *
   * @see #enumerate(Predicate)
   */
  public static final Predicate TRUE = type -> true;

  public static final short FIRST_TOKEN_INDEX = 1;
  private static final short MAX_INDEXED_TYPES = 15000;

  private static short size; // guarded by lock
  @NotNull
  private static volatile IElementType[] ourRegistry = EMPTY_ARRAY; // writes are guarded by lock
  @SuppressWarnings("RedundantStringConstructorCall")
  private static final Object lock = new String("registry lock");

  static {
    IElementType[] init = new IElementType[137];
    // have to start from one for some obscure compatibility reasons
    init[0] = new IElementType("NULL", Language.ANY, false);
    push(init);
  }

  @NotNull
  static IElementType[] push(@NotNull IElementType[] types) {
    synchronized (lock) {
      IElementType[] oldRegistry = ourRegistry;
      ourRegistry = types;
      size = (short)ContainerUtil.skipNulls(Arrays.asList(ourRegistry)).size();
      return oldRegistry;
    }
  }

  private final short myIndex;
  @NotNull
  private final String myDebugName;
  @NotNull
  private final Language myLanguage;

  /**
   * Creates and registers a new element type for the specified language.
   *
   * @param debugName the name of the element type, used for debugging purposes.
   * @param language  the language with which the element type is associated.
   */
  public IElementType(@NotNull String debugName, @Nullable Language language) {
    this(debugName, language, true);
  }


  /**
   * Allows to construct element types for some temporary purposes without registering them.
   * This is not default behavior and not recommended. A lot of other functionality (e.g. {@link TokenSet}) won't work with such element types.
   * Please use {@link #IElementType(String, Language)} unless you know what you're doing.
   */
  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  protected IElementType(@NotNull String debugName, @Nullable Language language, boolean register) {
    myDebugName = debugName;
    myLanguage = language == null ? Language.ANY : language;
    if (register) {
      synchronized (lock) {
        myIndex = size++;
        if (myIndex >= MAX_INDEXED_TYPES) {
          Map<Language, List<IElementType>> byLang = Stream.of(ourRegistry).filter(i->i!=null).collect(Collectors.groupingBy(ie -> ie.myLanguage));
          Map.Entry<Language, List<IElementType>> max = byLang.entrySet().stream().max(Comparator.comparingInt(e -> e.getValue().size())).get();
          List<IElementType> types = max.getValue();
          LOG.error("Too many element types registered. Out of (short) range. Most of element types (" + types.size() + ")" +
                    " were registered for '" + max.getKey() + "': " + StringUtil.first(StringUtil.join(types, ", "), 300, true));
        }
        IElementType[] newRegistry =
          myIndex >= ourRegistry.length ? ArrayUtil.realloc(ourRegistry, ourRegistry.length * 3 / 2 + 1, ARRAY_FACTORY) : ourRegistry;
        newRegistry[myIndex] = this;
        ourRegistry = newRegistry;
      }
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
  @NotNull
  public Language getLanguage() {
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

  @Override
  public int hashCode() {
    return myIndex >= 0 ? myIndex : super.hashCode();
  }

  @Override
  public String toString() {
    return myDebugName;
  }

  /**
   * Controls whitespace balancing behavior of PsiBuilder.
   * <p>By default, empty composite elements (containing no children) are bounded to the right (previous) neighbour, forming following tree:
   * <pre>
   *  [previous_element]
   *  [whitespace]
   *  [empty_element]
   *    &lt;empty&gt;
   *  [next_element]
   * </pre>
   * <p>Left-bound elements are bounded to the left (next) neighbour instead:
   * <pre>
   *  [previous_element]
   *  [empty_element]
   *    &lt;empty&gt;
   *  [whitespace]
   *  [next_element]
   * </pre>
   * <p>See com.intellij.lang.impl.PsiBuilderImpl.prepareLightTree() for details.
   * @return true if empty elements of this type should be bound to the left.
   */
  public boolean isLeftBound() {
    return false;
  }

  /**
   * Returns the element type registered at the specified index.
   *
   * @param idx the index for which the element type should be returned.
   * @return the element type at the specified index.
   * @throws IndexOutOfBoundsException if the index is out of registered elements' range.
   */
  public static IElementType find(short idx) {
    // volatile read; array always grows, never shrinks, never overwritten
    return ourRegistry[idx];
  }

  /**
   * Predicate for matching element types.
   *
   * @see IElementType#enumerate(Predicate)
   */
  @FunctionalInterface
  public interface Predicate {
    boolean matches(@NotNull IElementType type);
  }

  @TestOnly
  static short getAllocatedTypesCount() {
    synchronized (lock) {
      return size;
    }
  }

  /**
   * Enumerates all registered token types which match the specified predicate.
   *
   * @param p the predicate which should be matched by the element types.
   * @return the array of matching element types.
   */
  @NotNull
  public static IElementType[] enumerate(@NotNull Predicate p) {
    List<IElementType> matches = new ArrayList<>();
    for (IElementType value : ourRegistry) {
      if (value != null && p.matches(value)) {
        matches.add(value);
      }
    }
    return matches.toArray(new IElementType[0]);
  }
}