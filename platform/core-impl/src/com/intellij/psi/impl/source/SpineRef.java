// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.stubs.StubTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link SubstrateRef} that identifies a stubbed PSI element by its position in the file's
 * <em>stubbed spine</em> — the flat, declaration-order list of all stub-backed elements.
 * <p>
 * Unlike AST or stub references, a {@code SpineRef} is resilient to GC: it holds only the
 * owning file and an integer index, so it remains valid even after the AST or stub tree has been
 * collected and later reloaded. On access, it lazily resolves the index against whichever tree
 * is currently loaded:
 * <ul>
 *   <li>{@link #getNode()} forces the AST (via {@link PsiFileImpl#calcTreeElement()}) and looks
 *       up the AST node at the spine position.</li>
 *   <li>{@link #getStub()} / {@link #getGreenStub()} return the stub at the same position if a
 *       stub tree is available, or {@code null} if only the AST is loaded.</li>
 * </ul>
 * <p>
 * {@code SpineRef}s are installed by {@link FileTrees#switchToSpineRefs} when both stubs and AST
 * coexist, or when previously cached PSI needs to survive a tree reload. They are replaced by
 * strong AST-node references in {@link FileTrees#switchToStrongRefs()} before AST mutations.
 *
 * @see SubstrateRef
 * @see FileTrees
 * @see StubbedSpine
 */
final class SpineRef extends SubstrateRef {
  private final PsiFileImpl myFile;

  /** Zero-based position in the file's {@link StubbedSpine}. */
  private final int myIndex;

  SpineRef(@NotNull PsiFileImpl file, int index) {
    myFile = file;
    myIndex = index;
  }

  /** Forces AST loading if needed and returns the AST node at this spine position. */
  @Override
  public @NotNull ASTNode getNode() {
    return myFile.calcTreeElement().getStubbedSpine().getSpineNodes().get(myIndex);
  }

  /** Returns the stub at this spine position, or {@code null} if no stub tree is loaded (AST-only state). */
  @Override
  public @Nullable Stub getStub() {
    StubTree tree = myFile.getStubTree();
    return tree == null ? null : tree.getPlainList().get(myIndex);
  }

  /**
   * Returns the "green" stub at this spine position — the stub from the tree that may coexist
   * with a loaded AST — or {@code null} if no such tree is available.
   */
  @Override
  public @Nullable Stub getGreenStub() {
    StubTree tree = myFile.getGreenStubTree();
    return tree == null ? null : tree.getPlainList().get(myIndex);
  }

  @Override
  public boolean isValid() {
    return myFile.isValid();
  }

  @Override
  public @NotNull PsiFile getContainingFile() {
    return myFile;
  }
}
