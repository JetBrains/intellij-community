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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The common base interface for all elements of the PSI tree.
 */
public interface PsiElement extends UserDataHolder, Iconable {
  /**
   * The empty array of PSI elements which can be reused to avoid unnecessary allocations.
   */
  PsiElement[] EMPTY_ARRAY = new PsiElement[0];

  /**
   * Returns the project to which the PSI element belongs.
   *
   * @return the project instance.
   * @throws PsiInvalidElementAccessException if this element is invalid
   */
  @NotNull
  Project getProject() throws PsiInvalidElementAccessException;

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
   * @throws PsiInvalidElementAccessException if this element is invalid
   * @return the file instance, or null if the PSI element is not contained in a file (for example,
   * the element represents a package or directory).
   */
  PsiFile getContainingFile() throws PsiInvalidElementAccessException;

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
  @Nullable
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
  @NonNls
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

  /**
   * Checks if the text of this PSI element is equal to the specified character sequence.
   *
   * @param text the character sequence to compare with.
   * @return true if the text is equal, false otherwise.
   */
  boolean textMatches(@NotNull CharSequence text);

  /**
   * Checks if the text of this PSI element is equal to the text of the specified PSI element.
   *
   * @param element the element to compare the text with.
   * @return true if the text is equal, false otherwise.
   */
  boolean textMatches(@NotNull PsiElement element);

  /**
   * Checks if the text of this element contains the specified character.
   *
   * @param c the character to search for.
   * @return true if the character is found, false otherwise.
   */
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

  /**
   * Creates a copy of the file containing the PSI element and returns the corresponding
   * element in the created copy. Resolve operations performed on elements in the copy
   * of the file will resolve to elements in the copy, not in the original file. 
   *
   * @return the element in the file copy corresponding to this element.
   */
  PsiElement copy();

  /**
   * Adds a child to this PSI element.
   *
   * @param element the child element to add.
   * @return the element which was actually added (either <code>element</code> or its copy).
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   */
  PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException;

  /**
   * Adds a child to this PSI element, before the specified anchor element.
   *
   * @param element the child element to add.
   * @param anchor  the anchor before which the child element is inserted (must be a child of this PSI element)
   * @return the element which was actually added (either <code>element</code> or its copy).
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   */
  PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException;

  /**
   * Adds a child to this PSI element, after the specified anchor element.
   *
   * @param element the child element to add.
   * @param anchor  the anchor after which the child element is inserted (must be a child of this PSI element)
   * @return the element which was actually added (either <code>element</code> or its copy).
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   */
  PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException;

  /**
   * Checks if it is possible to add the specified element as a child to this element,
   * and throws an exception if the add is not possible. Does not actually modify anything.
   *
   * @param element the child element to check the add possibility.
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   * @deprecated not all PSI implementations implement this method correctly.
   */
  void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException;

  /**
   * Adds a range of elements as children to this PSI element.
   *
   * @param first the first child element to add.
   * @param last  the last child element to add (must have the same parent as <code>first</code>)
   * @return the first child element which was actually added (either <code>first</code> or its copy).
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   */
  PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException;

  /**
   * Adds a range of elements as children to this PSI element, before the specified anchor element.
   *
   * @param first   the first child element to add.
   * @param last    the last child element to add (must have the same parent as <code>first</code>)
   * @param anchor  the anchor before which the child element is inserted (must be a child of this PSI element)
   * @return the first child element which was actually added (either <code>first</code> or its copy).
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   */
  PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor) throws IncorrectOperationException;

  /**
   * Adds a range of elements as children to this PSI element, after the specified anchor element.
   *
   * @param first   the first child element to add.
   * @param last    the last child element to add (must have the same parent as <code>first</code>)
   * @param anchor  the anchor after which the child element is inserted (must be a child of this PSI element)
   * @return the first child element which was actually added (either <code>first</code> or its copy).
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   */
  PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException;

  /**
   * Deletes this PSI element from the tree.
   *
   * @throws IncorrectOperationException if the modification is not supported
   * or not possible for some reason (for example, the file containing the element is read-only).
   */
  void delete() throws IncorrectOperationException;

  /**
   * Checks if it is possible to delete the specified element from the tree,
   * and throws an exception if the add is not possible. Does not actually modify anything.
   *
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   * @deprecated not all PSI implementations implement this method correctly.
   */
  void checkDelete() throws IncorrectOperationException;

  /**
   * Deletes a range of children of this PSI element from the tree.
   *
   * @param first the first child to delete (must be a child of this PSI element)
   * @param last  the last child to delete (must be a child of this PSI element)
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   */
  void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException;

  /**
   * Replaces this PSI element (along with all its children) with another element
   * (along with the children).
   *
   * @param newElement the element to replace this element with.
   * @return the element which was actually inserted in the tree (either <code>newElement</code> or its copy)
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   */
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
   * Returns the reference associated with this PSI element. If the element has multiple
   * associated references (see {@link #getReferences()} for an example), returns the first
   * associated reference.
   *
   * @return the reference instance, or null if the PSI element does not have any
   * associated references.
   */
  @Nullable
  PsiReference getReference();

  /**
   * Returns all references associated with this PSI element. An element can be associated
   * with multiple references when, for example, the element is a string literal containing
   * multiple sub-strings which are valid full-qualified class names. If an element
   * contains only one text fragment which acts as a reference but the reference has
   * multiple possible targets, {@link PsiPolyVariantReference} should be used instead
   * of returning multiple references.
   *
   * @return the array of references, or an empty array if the element has no associated
   * references.
   */
  @NotNull PsiReference[] getReferences();

  /**
   * Returns a copyable user data object attached to this element.
   *
   * @param key the key for accessing the user data object.
   * @return the user data object, or null if no such object is found in the current element.
   * @see #putCopyableUserData(com.intellij.openapi.util.Key, Object)
   */
  @Nullable <T> T getCopyableUserData(Key<T> key);

  /**
   * Attaches a copyable user data object to this element. Copyable user data objects are copied
   * when the PSI elements are copied.
   *
   * @param key the key for accessing the user data object.
   * @param value the user data object to attach.
   * @see #getCopyableUserData(com.intellij.openapi.util.Key)
   */
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
  @Nullable PsiElement getContext();

  /**
   * Checks if an actual source or class file corresponds to the element. Non-physical elements include,
   * for example, PSI elements created for the watch expressions in the debugger.
   * Non-physical elements do not generate tree change events.
   * Also, {@link PsiDocumentManager#getDocument(PsiFile)} returns null for non-physical elements.
   *
   * @return true if the element is physical, false otherwise.
   */
  boolean isPhysical();

  /**
   * Returns the scope in which the declarations for the references in this PSI element are searched.
   *
   * @return the resolve scope instance.
   */
  @NotNull GlobalSearchScope getResolveScope();

  /**
   * Returns the scope in which references to this element are searched. The implementation of this
   * method is usually delegated to {@link com.intellij.psi.search.PsiSearchHelper#getUseScope(PsiElement)}.
   *
   * @return the search scope instance.
   */
  @NotNull SearchScope getUseScope();

  /**
   * Returns the AST node corresponding to the element.
   *
   * @return the AST node instance.
   */
  @Nullable
  ASTNode getNode();

  /**
   * toString() should never be presented to the user.
   */
  @NonNls String toString();
}
