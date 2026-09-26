// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The backing reference that connects a {@link StubBasedPsiElementBase} to its underlying
 * data source — an AST node, a stub, or a spine index.
 * <p>
 * A stub-based PSI element does not hold a direct pointer to its AST node or stub. Instead,
 * it delegates to a {@code SubstrateRef} whose concrete type determines how the element
 * resolves its tree data. The substrate can be swapped at runtime (via
 * {@link StubBasedPsiElementBase#setSubstrateRef}) as the file transitions between states:
 *
 * <table>
 *   <caption>Substrate implementations and their lifecycle</caption>
 *   <tr><th>Implementation</th><th>Holds</th><th>When used</th></tr>
 *   <tr>
 *     <td>{@link StubRef}</td>
 *     <td>Direct stub reference</td>
 *     <td>Initial state for elements created from stubs during indexing or first access</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #createAstRef strong AST ref}</td>
 *     <td>Direct AST node reference</td>
 *     <td>After {@link FileTrees#switchToStrongRefs()}, before AST mutation</td>
 *   </tr>
 *   <tr>
 *     <td>{@link SpineRef}</td>
 *     <td>File + spine index</td>
 *     <td>When stubs and AST coexist; survives GC of either tree</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #createInvalidRef invalid ref}</td>
 *     <td>Nothing (throws on access)</td>
 *     <td>After the element has been invalidated (e.g. stub cleared)</td>
 *   </tr>
 * </table>
 *
 * @see StubBasedPsiElementBase#setSubstrateRef
 * @see FileTrees
 * @see SpineRef
 */
@ApiStatus.Internal
public abstract class SubstrateRef {

  /** Returns the AST node for this element, potentially forcing AST loading. */
  public abstract @NotNull ASTNode getNode();

  /**
   * Returns the stub for this element, or {@code null} if the element is currently backed by AST.
   * Delegates to {@link PsiFileImpl#getStubTree()}, which returns {@code null} once the AST is
   * loaded — callers that need stubs even when AST is present should use {@link #getGreenStub()}.
   */
  public @Nullable Stub getStub() {
    return null;
  }

  /**
   * Returns the "green" stub — the stub from a tree that may coexist with a loaded AST —
   * or {@code null} if no stub tree is available at all. Unlike {@link #getStub()}, this does
   * not return {@code null} merely because the AST is loaded. Defaults to {@link #getStub()}.
   */
  public @Nullable Stub getGreenStub() {
    return getStub();
  }

  /** Returns {@code true} if the owning PSI element is still valid (its file has not been invalidated). */
  public abstract boolean isValid();

  /** Returns the {@link PsiFile} containing this element, or throws if the element is invalid. */
  public abstract @NotNull PsiFile getContainingFile();

  /**
   * Creates a substrate that throws {@link PsiInvalidElementAccessException} on any access.
   * Installed by {@link FileTrees#clearStub} when the element's backing tree is discarded.
   */
  static @NotNull SubstrateRef createInvalidRef(@NotNull StubBasedPsiElementBase<?> psi) {
    return new InvalidRef(psi);
  }

  /**
   * Creates a substrate that holds a strong reference to an AST node. Installed by
   * {@link FileTrees#switchToStrongRefs()} before AST mutations so that in-place tree
   * edits can update nodes without losing PSI identity.
   */
  public static @NotNull SubstrateRef createAstRef(@NotNull ASTNode node) {
    return new AstRef(node);
  }

  /**
   * Creates a substrate backed by a direct reference to a stub element.
   * This is the initial substrate for PSI elements created from stubs (e.g. during indexing
   * or stub-based resolve). {@link SubstrateRef#getNode()} is not supported in this state —
   * callers must switch to an AST-backed substrate first.
   */
  public static @NotNull SubstrateRef createStubRef(@NotNull StubElement<?> stub) {
    return new StubRef(stub);
  }

  public static boolean isStubRef(@NotNull SubstrateRef ref) {
    return ref instanceof StubRef;
  }
}
