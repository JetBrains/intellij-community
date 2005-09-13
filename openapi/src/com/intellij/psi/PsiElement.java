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
package com.intellij.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The common base interface for all elements of the PSI tree.
 */
public interface PsiElement extends UserDataHolder, Iconable {
  PsiElement[] EMPTY_ARRAY = new PsiElement[0];

  /**
   * Returns the project to which the PSI element belongs.
   *
   * @return the project instance.
   */
  Project getProject();

  /**
   * Returns the language of the PSI element.
   *
   * @return the language instance.
   */
  @NotNull Language getLanguage();

  /**
   * Returns the PSI manager for the project to which the PSI element belongs.
   *
   * @return the PSI manager instance.
   */
  PsiManager getManager();

  /**
   * Returns the array of children for the PSI element.
   *
   * @return the array of child elements.
   */
  @NotNull PsiElement[] getChildren();

  /**
   * Returns the parent of the PSI element.
   *
   * @return the parent of the element, or null if the element has no parent.
   */
  PsiElement getParent();

  /**
   * Returns the first child of the PSI element.
   *
   * @return the first child, or null if the element has no children.
   */
  @Nullable PsiElement getFirstChild();

  /**
   * Returns the last child of the PSI element.
   *
   * @return the last child, or null if the element has no children.
   */
  @Nullable  PsiElement getLastChild();

  /**
   * Returns the next sibling of the PSI element.
   *
   * @return the next sibling, or null if the node is the last in the list of siblings.
   */
  @Nullable  PsiElement getNextSibling();

  /**
   * Returns the previous sibling of the PSI element.
   *
   * @return the previous sibling, or null if the node is the first in the list of siblings.
   */
  @Nullable  PsiElement getPrevSibling();

  /**
   * Returns the file containing the PSI element.
   *
   * @return the file instance.
   */
  PsiFile getContainingFile();

  /**
   * Returns the text range in the document occupied by the PSI element.
   *
   * @return the text range.
   */
  TextRange getTextRange();

  /**
   * Returns the text offset of the PSI element relative to its parent.
   *
   * @return the relative offset.
   */
  int getStartOffsetInParent();

  /**
   * Returns the length of text of the PSI element.
   *
   * @return the text length.
   */
  int getTextLength();

  /**
   * Finds a leaf PSI element at the specified offset from the start of the text range of this node.
   *
   * @param offset the relative offset for which the PSI element is requested.
   * @return the element at the offset, or null if none is found.
   */
  PsiElement findElementAt(int offset);

  /**
   * Finds a reference at the specified offset from the start of the text range of this node.
   *
   * @param offset the relative offset for which the reference is requested.
   * @return the reference at the offset, or null if none is found.
   */
  @Nullable
  PsiReference findReferenceAt(int offset);

  /**
   * Returns the offset in the file to which the caret should be placed
   * when performing the navigation to the element. (For classes implementing
   * {@link PsiNamedElement}, this should return the offset in the file of the
   * name identifier.)
   *
   * @return the offset of the PSI element.
   */
  int getTextOffset();

  /**
   * Returns the text of the PSI element.
   *
   * @return the element text.
   */
  String getText();

  /**
   * Returns the text of the PSI element as a character array.
   *
   * @return the element text as a character array.
   */
  @NotNull char[] textToCharArray();

  /**
   * Returns the PSI element which should be used as a navigation target
   * when navigation to this PSI element is requested. The method can either
   * return <code>this</code> or substitute a different element if this element
   * does not have an associated file and offset. (For example, if the source code
   * of a library is attached to a project, the navigation element for a compiled
   * library class is its source class.)
   *
   * @return the navigation target element.
   */
  PsiElement getNavigationElement();

  /**
   * Returns the PSI element which corresponds to this element and belongs to
   * either the project source path or class path. The method can either return
   * <code>this</code> or substitute a different element if this element does
   * not belong to the source path or class path. (For example, the original
   * element for a library source file is the corresponding compiled class file.)
   *
   * @return the original element.
   */
  PsiElement getOriginalElement();

  //Q: get rid of these methods?
  boolean textMatches(@NotNull CharSequence text);

  boolean textMatches(@NotNull PsiElement element);

  boolean textContains(char c);

  /**
   * Passes the element to the specified visitor.
   *
   * @param visitor the visitor to pass the element to.
   */
  void accept(@NotNull PsiElementVisitor visitor);

  /**
   * Passes the children of the element to the specified visitor.
   *
   * @param visitor the visitor to pass the children to.
   */
  void acceptChildren(@NotNull PsiElementVisitor visitor);

  PsiElement copy();

  PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException;

  PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException;

  PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException;

  void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException;

  PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException;

  PsiElement addRangeBefore(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException;

  PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException;

  void delete() throws IncorrectOperationException;

  void checkDelete() throws IncorrectOperationException;

  void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException;

  PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException;

  /**
   * Checks if the PSI element corresponds to the current state of the underlying
   * document. The element is no longer valid after the document has been reparsed
   * and a new PSI tree has been built for it.
   *
   * @return true if the element is valid, false otherwise.
   */
  boolean isValid();

  /**
   * Checks if the contents of the element can be modified (if it belongs to a
   * non-read-only source file.)
   *
   * @return true if the element can be modified, false otherwise.
   */
  boolean isWritable();

  /**
   * Returns the reference associated with this PSI element.
   *
   * @return the reference instance, or null if the PSI element does not have any
   * associated references.
   */
  @Nullable
  PsiReference getReference();

  /**
   * Returns all references associated with this PSI element. An element can be associated
   * with multiple references when, for example, the element is a string literal containing
   * multiple sub-strings which are valid full-qualified class names.
   *
   * @return the array of references, or an empty array if the element has no associated
   * references.
   */
  @NotNull PsiReference[] getReferences();

  <T> T getCopyableUserData(Key<T> key);

  <T> void putCopyableUserData(Key<T> key, T value);

  /**
   * Passes the declarations contained in this PSI element and its children
   * for processing to the specified scope processor.
   *
   * @param processor   the processor receiving the declarations.
   * @param substitutor the class providing the mapping between type parameters and their values.
   * @param lastParent  the child of this element which was processed during the previous
   *                    step of the tree up walk (declarations under this element do not need
   *                    to be processed again)
   * @param place       the original element from which the tree up walk was initiated.
   * @return true if the declaration processing should continue or false if it should be stopped.
   */
  boolean processDeclarations(PsiScopeProcessor processor,
                              PsiSubstitutor substitutor,
                              PsiElement lastParent,
                              PsiElement place);

  /**
   * Returns the element which should be used as the parent of this element in a tree up
   * walk during a resolve operation. For most elements, this returns <code>getParent()</code>,
   * but the context can be overridden for some elements like code fragments (see
   * {@link PsiElementFactory#createCodeBlockCodeFragment(String, PsiElement, boolean)}). 
   *
   * @return the resolve context element.
   */
  PsiElement getContext();

  boolean isPhysical();

  GlobalSearchScope getResolveScope();
  @NotNull SearchScope getUseScope();

  /**
   * Returns the AST node corresponding to the element.
   *
   * @return the AST node instance.
   */
  ASTNode getNode();
}
