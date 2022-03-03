// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.model.psi.PsiSymbolDeclaration;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.ArrayFactory;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * The common base interface for all elements of the PSI tree.
 * <p/>
 * Please see <a href="https://plugins.jetbrains.com/docs/intellij/psi-elements.html">IntelliJ Platform Docs</a>
 * for high-level overview.
 */
public interface PsiElement extends UserDataHolder, Iconable {
  /**
   * The empty array of PSI elements which can be reused to avoid unnecessary allocations.
   */
  PsiElement[] EMPTY_ARRAY = new PsiElement[0];

  ArrayFactory<PsiElement> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiElement[count];

  /**
   * Returns the project to which the PSI element belongs.
   *
   * @return the project instance.
   * @throws PsiInvalidElementAccessException if this element is invalid
   */
  @NotNull
  @Contract(pure=true)
  Project getProject() throws PsiInvalidElementAccessException;

  /**
   * Returns the language of the PSI element.
   *
   * @return the language instance.
   */
  @NotNull
  @Contract(pure=true)
  Language getLanguage();

  /**
   * Returns the PSI manager for the project to which the PSI element belongs.
   *
   * @return the PSI manager instance.
   */
  @Contract(pure=true)
  PsiManager getManager();

  /**
   * Returns the array of children for the PSI element.
   * Important: In some implementations children are only composite elements, i.e. not a leaf elements
   *
   * @return the array of child elements.
   */
  @Contract(pure=true)
  PsiElement @NotNull [] getChildren();

  /**
   * Returns the parent of the PSI element.
   *
   * @return the parent of the element, or null if the element has no parent.
   */
  @Contract(pure=true)
  PsiElement getParent();

  /**
   * Returns the first child of the PSI element.
   *
   * @return the first child, or null if the element has no children.
   */
  @Contract(pure=true)
  PsiElement getFirstChild();

  /**
   * Returns the last child of the PSI element.
   *
   * @return the last child, or null if the element has no children.
   */
  @Contract(pure=true)
  PsiElement getLastChild();

  /**
   * Returns the next sibling of the PSI element.
   *
   * @return the next sibling, or null if the node is the last in the list of siblings.
   */
  @Contract(pure=true)
  PsiElement getNextSibling();

  /**
   * Returns the previous sibling of the PSI element.
   *
   * @return the previous sibling, or null if the node is the first in the list of siblings.
   */
  @Contract(pure=true)
  PsiElement getPrevSibling();

  /**
   * Returns the file containing the PSI element.
   * <p></p>
   * Note: this method might need to traverse the whole AST up, which can be slow in deep trees, so invoking this method should be avoided if possible.
   *
   * @return the file instance, or null if the PSI element is not contained in a file (for example,
   *         the element represents a package or directory).
   * @throws PsiInvalidElementAccessException
   *          if this element is invalid
   */
  @Contract(pure=true)
  PsiFile getContainingFile() throws PsiInvalidElementAccessException;

  /**
   * Returns the text range in the document occupied by the PSI element.
   * <p></p>
   * Note: it works in <i>O(tree_depth)</i> time, which can be slow in deep trees, so invoking this method should be avoided if possible.
   *
   * @return the text range.
   */
  @Contract(pure=true)
  TextRange getTextRange();

  /**
   * @return text range of this element relative to its parent
   */
  @Contract(pure = true)
  @NotNull
  default TextRange getTextRangeInParent() {
    return TextRange.from(getStartOffsetInParent(), getTextLength());
  }

  /**
   * Returns the text offset of the PSI element relative to its parent.
   *
   * @return the relative offset.
   */
  @Contract(pure=true)
  int getStartOffsetInParent();

  /**
   * Returns the length of text of the PSI element.
   *
   * @return the text length.
   */
  @Contract(pure=true)
  int getTextLength();

  /**
   * Finds a leaf PSI element at the specified offset from the start of the text range of this node.
   *
   * @param offset the relative offset for which the PSI element is requested.
   * @return the element at the offset, or null if none is found.
   */
  @Nullable
  @Contract(pure=true)
  PsiElement findElementAt(int offset);

  /**
   * Finds a reference at the specified offset from the start of the text range of this node.
   *
   * @param offset the relative offset for which the reference is requested.
   * @return the reference at the offset, or null if none is found.
   */
  @Nullable
  @Contract(pure=true)
  PsiReference findReferenceAt(int offset);

  /**
   * Returns the offset in the file to which the caret should be placed
   * when performing the navigation to the element. (For classes implementing
   * {@link PsiNamedElement}, this should return the offset in the file of the
   * name identifier.)
   *
   * @return the offset of the PSI element.
   */
  @Contract(pure=true)
  int getTextOffset();

  /**
   * Returns the text of the PSI element.
   * <p></p>
   * Note: This call requires traversing whole subtree, so it can be expensive for composite elements, and should be avoided if possible.
   *
   * @return the element text.
   * @see #textMatches
   * @see #textContains
   */
  @Contract(pure=true)
  @NlsSafe
  String getText();

  /**
   * Returns the text of the PSI element as a character array.
   *
   * @return the element text as a character array.
   */
  @Contract(pure=true)
  char @NotNull [] textToCharArray();

  /**
   * Returns the PSI element which should be used as a navigation target
   * when navigation to this PSI element is requested. The method can either
   * return {@code this} or substitute a different element if this element
   * does not have an associated file and offset. (For example, if the source code
   * of a library is attached to a project, the navigation element for a compiled
   * library class is its source class.)
   *
   * @return the navigation target element.
   */
  @Contract(pure=true)
  PsiElement getNavigationElement();

  /**
   * Returns the PSI element which corresponds to this element and belongs to
   * either the project source path or class path. The method can either return
   * {@code this} or substitute a different element if this element does
   * not belong to the source path or class path. (For example, the original
   * element for a library source file is the corresponding compiled class file.)
   *
   * @return the original element.
   */
  @Contract(pure=true)
  PsiElement getOriginalElement();

  //Q: get rid of these methods?

  /**
   * Checks if the text of this PSI element is equal to the specified character sequence.
   *
   * @param text the character sequence to compare with.
   * @return true if the text is equal, false otherwise.
   */
  @Contract(pure=true)
  boolean textMatches(@NotNull @NonNls CharSequence text);

  /**
   * Checks if the text of this PSI element is equal to the text of the specified PSI element.
   *
   * @param element the element to compare the text with.
   * @return true if the text is equal, false otherwise.
   */
  @Contract(pure=true)
  boolean textMatches(@NotNull PsiElement element);

  /**
   * Checks if the text of this element contains the specified character.
   *
   * @param c the character to search for.
   * @return true if the character is found, false otherwise.
   */
  @Contract(pure=true)
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
   * <p>
   * For light elements, may return {@code null}.
   *
   * @return the element in the file copy corresponding to this element.
   */
  PsiElement copy();

  /**
   * Adds a child to this PSI element.
   *
   * @param element the child element to add.
   * @return the element which was actually added (either {@code element} or its copy).
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   */
  PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException;

  /**
   * Adds a child to this PSI element, before the specified anchor element.
   *
   * @param element the child element to add.
   * @param anchor  the anchor before which the child element is inserted (must be a child of this PSI element)
   * @return the element which was actually added (either {@code element} or its copy).
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   */
  PsiElement addBefore(@NotNull PsiElement element, @Nullable PsiElement anchor) throws IncorrectOperationException;

  /**
   * Adds a child to this PSI element, after the specified anchor element.
   *
   * @param element the child element to add.
   * @param anchor  the anchor after which the child element is inserted (must be a child of this PSI element)
   * @return the element which was actually added (either {@code element} or its copy).
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   */
  PsiElement addAfter(@NotNull PsiElement element, @Nullable PsiElement anchor) throws IncorrectOperationException;

  /**
   * Checks if it is possible to add the specified element as a child to this element,
   * and throws an exception if the add is not possible. Does not actually modify anything.
   *
   * @param element the child element to check the addition possibility.
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   * @deprecated not all PSI implementations implement this method correctly.
   */
  @Deprecated
  void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException;

  /**
   * Adds a range of elements as children to this PSI element.
   *
   * @param first the first child element to add.
   * @param last  the last child element to add (must have the same parent as {@code first})
   * @return the first child element which was actually added (either {@code first} or its copy).
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   */
  PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException;

  /**
   * Adds a range of elements as children to this PSI element, before the specified anchor element.
   *
   * @param first  the first child element to add.
   * @param last   the last child element to add (must have the same parent as {@code first})
   * @param anchor the anchor before which the child element is inserted (must be a child of this PSI element)
   * @return the first child element which was actually added (either {@code first} or its copy).
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   */
  PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor) throws IncorrectOperationException;

  /**
   * Adds a range of elements as children to this PSI element, after the specified anchor element.
   *
   * @param first  the first child element to add.
   * @param last   the last child element to add (must have the same parent as {@code first})
   * @param anchor the anchor after which the child element is inserted (must be a child of this PSI element)
   * @return the first child element which was actually added (either {@code first} or its copy).
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   */
  PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException;

  /**
   * Deletes this PSI element from the tree.
   *
   * @throws IncorrectOperationException if the modification is not supported
   *                                     or not possible for some reason (for example, the file containing the element is read-only).
   */
  void delete() throws IncorrectOperationException;

  /**
   * Checks if it is possible to delete the specified element from the tree,
   * and throws an exception if the addition is not possible. Does not actually modify anything.
   *
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   * @deprecated not all PSI implementations implement this method correctly.
   */
  @Deprecated
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
   * @return the element which was actually inserted in the tree (either {@code newElement} or its copy)
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   */
  PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException;

  /**
   * Checks if this PSI element is valid. Valid elements and their hierarchy members
   * can be accessed for reading and writing. Valid elements can still correspond to
   * underlying documents whose text is different, when those documents have been changed
   * and not yet committed ({@link PsiDocumentManager#commitDocument(com.intellij.openapi.editor.Document)}).
   * (In this case an attempt to change PSI will result in an exception).<br><br>
   *
   * Most method calls on invalid PSI result in {@link PsiInvalidElementAccessException}.
   * Once invalid, elements can't become valid again.
   * Elements become invalid in following cases:
   * <ul>
   *   <li>They have been deleted via PSI operation (e.g. {@link #delete()})</li>
   *   <li>They have been deleted as a result of an incremental reparse (document commit)</li>
   *   <li>Their containing file has been changed externally, or renamed so that its PSI had to be rebuilt from scratch</li>
   * </ul>
   *
   * Note that calls to this method are expected to be rare and can even be considered a code smell. In general,
   * when you're given some PSI, you should assume it's valid. If it turns out to be invalid, it's the responsibility
   * of those who gave you this PSI, not yours, and they should be fixed, not your code.<br><br>
   *
   * The rare circumstances where {@code isValid} check makes sense
   * are those where it's obvious from the surrounding code why the PSI could become invalid. For example, right after a PSI modification
   * or at the start of a read action (because any write action could've invalidated the PSI between read actions,
   * and you should never expect PSI to survive that). And even in these circumstances, please consider alternatives
   * that support PSI restoration, e.g. {@link SmartPsiElementPointer}s.
   *
   * @return true if the element is valid, false otherwise.
   * @see com.intellij.psi.util.PsiUtilCore#ensureValid(PsiElement)
   */
  @Contract(pure=true)
  boolean isValid();

  /**
   * Checks if the contents of the element can be modified (if it belongs to a
   * non-read-only source file.)
   *
   * @return true if the element can be modified, false otherwise.
   */
  @Contract(pure=true)
  boolean isWritable();

  /**
   * The contents of the returned collection are copied after the method returns,
   * the platform doesn't store or modify the returned collection.
   *
   * @return collection of declarations in this element, or empty collection if there are no such declarations
   * @see com.intellij.model.psi.PsiSymbolDeclarationProvider
   */
  @Experimental
  default @NotNull Collection<? extends @NotNull PsiSymbolDeclaration> getOwnDeclarations() {
    return Collections.emptyList();
  }

  /**
   * The returned references are expected to be used by language support,
   * for example in Java `foo` element in `foo = 42` expression has a reference,
   * which is used by Java language support to compute expected type of the assignment.
   * <p>
   * On the other hand {@code "bar"} literal in {@code new File("bar")} is a string literal,
   * and from Java language perspective it has no references,
   * but the framework support "knows" that this literal contains the reference to a file.
   * These are external references.
   * <p/>
   * The contents of the returned collection are copied after the method returns,
   * the platform doesn't store or modify the returned collection.
   *
   * @return collection of references from this element, or empty collection if there are no such references
   * @see com.intellij.model.psi.PsiExternalReferenceHost
   * @see com.intellij.model.psi.PsiSymbolReferenceService#getReferences(PsiElement)
   */
  @Experimental
  default @NotNull Collection<? extends @NotNull PsiSymbolReference> getOwnReferences() {
    return Collections.emptyList();
  }

  /**
   * Returns the reference from this PSI element to another PSI element (or elements), if one exists.
   * If the element has multiple associated references (see {@link #getReferences()}
   * for an example), returns the first associated reference.
   *
   * @return the reference instance, or null if the PSI element does not have any
   *         associated references.
   * @see com.intellij.psi.search.searches.ReferencesSearch
   */
  @Nullable
  @Contract(pure=true)
  PsiReference getReference();

  /**
   * Returns all references from this PSI element to other PSI elements. An element can
   * have multiple references when, for example, the element is a string literal containing
   * multiple sub-strings which are valid full-qualified class names. If an element
   * contains only one text fragment which acts as a reference but the reference has
   * multiple possible targets, {@link PsiPolyVariantReference} should be used instead
   * of returning multiple references.
   * <p/>
   * Actually, it's preferable to call {@link PsiReferenceService#getReferences} instead
   * as it allows adding references by plugins when the element implements {@link ContributedReferenceHost}.
   *
   * @return the array of references, or an empty array if the element has no associated
   *         references.
   * @see PsiReferenceService#getReferences
   * @see com.intellij.psi.search.searches.ReferencesSearch
   */
  @Contract(pure=true)
  PsiReference @NotNull [] getReferences();

  /**
   * Returns a copyable user data object attached to this element.
   *
   * @param key the key for accessing the user data object.
   * @return the user data object, or null if no such object is found in the current element.
   * @see #putCopyableUserData(Key, Object)
   */
  @Nullable
  @Contract(pure = true)
  <T> T getCopyableUserData(@NotNull Key<T> key);

  /**
   * Attaches a copyable user data object to this element. Copyable user data objects are copied
   * when the PSI elements are copied.
   *
   * @param key   the key for accessing the user data object.
   * @param value the user data object to attach.
   * @see #getCopyableUserData(Key)
   */
  <T> void putCopyableUserData(@NotNull Key<T> key, @Nullable T value);

  /**
   * Passes the declarations contained in this PSI element and its children
   * for processing to the specified scope processor.
   *
   * @param processor  the processor receiving the declarations.
   * @param lastParent the child of this element has been processed during the previous
   *                   step of the tree up walk (declarations under this element do not need
   *                   to be processed again)
   * @param place      the original element from which the tree walk-up was initiated.
   * @return true if the declaration processing should continue or false if it should be stopped.
   */
  boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                              @NotNull ResolveState state,
                              @Nullable PsiElement lastParent,
                              @NotNull PsiElement place);

  /**
   * Returns the element which should be used as the parent of this element in a tree up
   * walk during a resolve operation. For most elements, this returns {@code getParent()},
   * but the context can be overridden for some elements like code fragments (see
   * {@link JavaCodeFragmentFactory#createCodeBlockCodeFragment(String, PsiElement, boolean)}).
   *
   * @return the resolve context element.
   */
  @Nullable
  @Contract(pure=true)
  PsiElement getContext();

  /**
   * Checks if an actual source or class file corresponds to the element. Non-physical elements include,
   * for example, PSI elements created for the watch expressions in the debugger.
   * Non-physical elements do not generate tree change events.
   * Also, {@link PsiDocumentManager#getDocument(PsiFile)} returns null for non-physical elements.
   * Not to be confused with {@link FileViewProvider#isPhysical()}.
   *
   * @return true if the element is physical, false otherwise.
   */
  @Contract(pure=true)
  boolean isPhysical();

  /**
   * Returns the scope in which the declarations for the references in this PSI element are searched.
   *
   * @return the resolve scope instance.
   */
  @NotNull
  @Contract(pure=true)
  GlobalSearchScope getResolveScope();

  /**
   * Returns the scope in which references to this element are searched.
   *
   * @return the search scope instance.
   * @see com.intellij.psi.search.PsiSearchHelper#getUseScope(PsiElement)
   */
  @NotNull
  @Contract(pure=true)
  SearchScope getUseScope();

  /**
   * Returns the AST node corresponding to the element.
   *
   * @return the AST node instance.
   */
  @Contract(pure=true)
  ASTNode getNode();

  /**
   * toString() should never be presented to the user.
   */
  @Override
  @NonNls
  @Contract(pure=true)
  String toString();

  /**
   * This method shouldn't be called by clients directly, because there are no guarantees of it being symmetric.
   * It's called by {@link PsiManager#areElementsEquivalent(PsiElement, PsiElement)} internally, which clients should invoke instead.<p/>
   *
   * Implementations of this method should return {@code true} if the parameter is resolve-equivalent to {@code this}, i.e. it represents
   * the same entity from the language perspective. See also {@link PsiManager#areElementsEquivalent(PsiElement, PsiElement)} documentation.
   */
  @Contract(pure=true)
  boolean isEquivalentTo(PsiElement another);
}
