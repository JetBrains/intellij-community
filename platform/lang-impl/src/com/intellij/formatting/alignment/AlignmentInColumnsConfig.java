/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.formatting.alignment;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Encapsulates information necessary for correct <code>'align in columns'</code> processing.
 * <p/>
 * Thread-safe.
 * <p/>
 * <b>Note:</b> this class doesn't provide custom realization for {@link #hashCode()} and {@link #equals(Object)} at the moment.
 * Feel free to add them as necessary.
 *
 * @author Denis Zhdanov
 * @since May 24, 2010 3:17:40 PM
 * @see AlignmentInColumnsHelper
 */
public class AlignmentInColumnsConfig {

  private final Set<IElementType> myStopMultilineCheckElementTypes = new HashSet<IElementType>();
  private final Set<IElementType> myTargetDeclarationTypes = new HashSet<IElementType>();
  private final TokenSet myWhiteSpaceTokenTypes;
  private final TokenSet myCommentTokenTypes;
  private final IElementType myEqualType;

  /**
   * Delegates object construction routine to {@link #AlignmentInColumnsConfig(Collection, TokenSet, TokenSet, IElementType, Collection)}
   * via wrapping given arguments in order to correspond to its signature.
   *
   * @param stopMultilineCheckElementTypes    stop multiline check element types
   * @param whiteSpaceTokenTypes              white space token types
   * @param commentTokenTypes                 comment token types
   * @param equalType                         <code>'='</code> token type
   * @param targetDeclarationTypes            target variable declaration sub-elements token types
   */
  public AlignmentInColumnsConfig(IElementType stopMultilineCheckElementTypes,
                                  TokenSet whiteSpaceTokenTypes,
                                  TokenSet commentTokenTypes,
                                  IElementType equalType,
                                  IElementType ... targetDeclarationTypes)
  {
    this(Collections.singleton(stopMultilineCheckElementTypes), whiteSpaceTokenTypes, commentTokenTypes, equalType,
         targetDeclarationTypes);
  }

  /**
   * Creates new <code>AlignmentInColumnsConfig</code> object that is used to tweak <code>'align in columns'</code> processing.
   * <p/>
   * 'Alignment in columns' means formatting code as shown below:
   * <p/>
   * <pre>
   *     double start = 1;
   *     int    end   = 2;
   *     private int tmp = 3;
   *     private   int    tmp2;
   *     protected double tmp3;
   * </pre>
   *
   * @param stopMultilineCheckElementTypes    <code>'align in column'</code> algorithm performs number of checks in order to decide
   *                                          if two variable declarations should be aligned in columns. One of that checks is
   *                                          examination for sub-elements consistency. E.g. <code>'int end = 2'</code> statement
   *                                          from example above is not aligned to <code>'private int tmp = 3;'</code> because the
   *                                          former doesn't have modifier. Element types given here defines boundary for such
   *                                          a checks, e.g. we can define type of <code>'='</code> element to be stop check type
   *                                          for example above
   * @param whiteSpaceTokenTypes              defines types of the tokens that should be treated as a white space
   * @param commentTokenTypes                 defines types of the tokens that should be treated as comments
   * @param equalType                         <code>'align in column'</code> algorithm doesn't align declarations and definitions.
   *                                          E.g. <code>'private int tmp = 3'</code> is not aligned to <code>'private int tmp2'</code>
   *                                          at example above. Given type defines type of <code>'='</code> token to use
   * @param targetDeclarationTypes            defines variable declaration sub-element types to be aligned. Example above
   *                                          shows alignment for <code>'modifier list'</code>, <code>'type reference'</code>,
   *                                          <code>'identifier'</code> and <code>'='</code> types. The general idea of this property
   *                                          is to let avoid alignment of unnecessary types, e.g. variable definition expressions
   */
  public AlignmentInColumnsConfig(Collection<IElementType> stopMultilineCheckElementTypes,
                                  TokenSet whiteSpaceTokenTypes,
                                  TokenSet commentTokenTypes,
                                  IElementType equalType,
                                  IElementType... targetDeclarationTypes)
  {
    myStopMultilineCheckElementTypes.addAll(stopMultilineCheckElementTypes);
    myWhiteSpaceTokenTypes = whiteSpaceTokenTypes;
    myCommentTokenTypes = commentTokenTypes;
    myEqualType = equalType;
    Collections.addAll(myTargetDeclarationTypes, targetDeclarationTypes);
  }

  /**
   * @return    <code>'stop multiline check element types'</code> property value
   * @see #AlignmentInColumnsConfig(Collection, TokenSet, TokenSet, IElementType, Collection) for more information about property meaning
   */
  public Set<IElementType> getStopMultilineCheckElementTypes() {
    return myStopMultilineCheckElementTypes;
  }

  /**
   * @return    <code>'white space token types'</code> property value
   * @see #AlignmentInColumnsConfig(Collection, TokenSet, TokenSet, IElementType, Collection) for more information about property meaning
   */
  public TokenSet getWhiteSpaceTokenTypes() {
    return myWhiteSpaceTokenTypes;
  }

  /**
   * @return    <code>'comment token types'</code> property value
   * @see #AlignmentInColumnsConfig(Collection, TokenSet, TokenSet, IElementType, Collection) for more information about property meaning
   */
  public TokenSet getCommentTokenTypes() {
    return myCommentTokenTypes;
  }

  /**
   * @return    <code>'equal type'</code> property value
   * @see #AlignmentInColumnsConfig(Collection, TokenSet, TokenSet, IElementType, Collection) for more information about property meaning
   */
  public IElementType getEqualType() {
    return myEqualType;
  }

  public Set<IElementType> getTargetDeclarationTypes() {
    return myTargetDeclarationTypes;
  }
}
