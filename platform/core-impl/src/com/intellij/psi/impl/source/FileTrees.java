// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.stubs.PsiFileStubImpl;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubTree;
import com.intellij.psi.stubs.StubTreeLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.intellij.psi.stubs.StubInconsistencyReporter.StubTreeAndIndexDoNotMatchSource.FileTreesPsiReconciliation;
import static com.intellij.reference.SoftReference.deref;
import static com.intellij.reference.SoftReference.dereference;

/**
 * Immutable snapshot of the backing trees (stubs and/or AST) for a single {@link PsiFileImpl}.
 * <p>
 * A {@code FileTrees} instance is never mutated; every state transition (loading AST, attaching stubs,
 * clearing stubs, switching reference modes) produces a new instance that is atomically swapped into
 * {@code PsiFileImpl.myTrees}.
 * <p>
 * The class manages three logical states:
 * <ol>
 *   <li><b>Stub-only</b> — {@link #myStub} is set, {@link #myTreeElementPointer} is {@code null}.
 *       This is the cheapest state: the file's declaration structure is available via stubs loaded
 *       from the index without parsing.</li>
 *   <li><b>AST-only</b> — {@link #myTreeElementPointer} is set, {@link #myStub} is {@code null}.
 *       The full syntax tree is in memory; stubs have been cleared (or were never loaded).</li>
 *   <li><b>Both (green stub)</b> — both references are set. This transient state exists while the
 *       AST is being loaded on top of already-existing stubs (or vice versa). {@link #syncPsiWithStub}
 *       ensures that PSI objects are shared between the two trees so that object identity is preserved.</li>
 * </ol>
 * <p>
 * PSI identity preservation across GC/reload is handled by the <em>spine ref</em> mechanism:
 * {@link #myRefToPsi} holds {@link WeakReference}s to every stubbed PSI element ever handed out.
 * When stubs or AST are reloaded, {@link #syncPsiWithStub} re-binds the new tree nodes to these
 * cached PSI objects instead of creating duplicates.
 *
 * @see PsiFileImpl#loadTreeElement()
 * @see PsiFileImpl#setStubTree
 * @see SpineRef
 */
final class FileTrees {
  private static final Logger LOG = Logger.getInstance(FileTrees.class);

  /**
   * Index 0 in the stubbed spine is the file-level PSI element itself, which is always
   * reachable through {@link #myFile} and does not need tracking in {@link #myRefToPsi}.
   */
  private static final int firstNonFilePsiIndex = 1;

  private final @NotNull PsiFileImpl myFile;

  /** Soft reference to the stub tree; {@code null} when no stubs are attached. */
  private final @Nullable Reference<StubTree> myStub;

  /**
   * Reference to the AST root. The supplier is a {@link SoftReference} or {@link WeakReference}
   * for physical files (allowing the GC to reclaim the AST under memory pressure),
   * or a strong lambda for non-physical files (e.g. {@code DummyHolder}).
   */
  private final @Nullable Supplier<? extends FileElement> myTreeElementPointer;

  /**
   * Weak references to every stubbed PSI element that has been handed out, indexed by spine position.
   * When non-{@code null}, each PSI element uses a {@link SpineRef} as its substrate, allowing it to
   * lazily resolve to whichever tree (stubs or AST) is currently loaded.
   * <p>
   * Set to {@code null} when only one tree source exists and no previously cached PSI needs tracking,
   * or after {@link #switchToStrongRefs()} pins all PSI directly to AST nodes.
   */
  private final @Nullable Reference<StubBasedPsiElementBase<?>> @Nullable [] myRefToPsi;

  private FileTrees(@NotNull PsiFileImpl file,
                    @Nullable Reference<StubTree> stub,
                    @Nullable Supplier<? extends FileElement> ast,
                    @Nullable Reference<StubBasedPsiElementBase<?>> @Nullable [] refToPsi) {
    myFile = file;
    myStub = stub;
    myTreeElementPointer = ast;
    myRefToPsi = refToPsi;
  }

  /** Returns the stub tree if it is still reachable (not yet GC-ed), or {@code null}. */
  @Nullable
  StubTree derefStub() {
    return dereference(myStub);
  }

  /** Returns the AST root if it is still reachable (not yet GC-ed), or {@code null}. */
  @Nullable
  FileElement derefTreeElement() {
    return deref(myTreeElementPointer);
  }

  /**
   * Transitions all tracked spine PSI from {@link SpineRef} to strong AST-node references.
   * Called by {@link PsiFileImpl#beforeAstChange()} before the AST is mutated, so that
   * the mutation can update the tree in place without losing PSI identity.
   * After this call, {@link #myRefToPsi} is no longer needed and is dropped.
   *
   * @return a new {@code FileTrees} with {@code myRefToPsi == null}, or {@code this} if already in strong-ref mode
   */
  @NotNull FileTrees switchToStrongRefs() {
    if (myRefToPsi == null) return this;

    forEachCachedPsi(psi -> {
      ASTNode node = psi.getNode();
      LOG.assertTrue(node.getPsi() == psi);
      psi.setSubstrateRef(SubstrateRef.createAstRef(node));
    });

    return new FileTrees(myFile, myStub, myTreeElementPointer, null);
  }

  void assertConsistency(PsiFile other) {
    if (this.myFile != other) {
      LOG.error("Attempt to attach FileTree to an alien PsiFile: expected " + myFile + ", got " + other);
    }
  }

  private void forEachCachedPsi(@NotNull Consumer<? super StubBasedPsiElementBase<?>> consumer) {
    assert myRefToPsi != null;
    for (Reference<StubBasedPsiElementBase<?>> t : myRefToPsi) {
      StubBasedPsiElementBase<?> psi = t == null ? null : t.get();
      if (psi != null) {
        consumer.accept(psi);
      }
    }
  }

  private boolean hasCachedPsi() {
    Reference<StubBasedPsiElementBase<?>>[] refToPsi = myRefToPsi;
    if (refToPsi != null) {
      for (Reference<StubBasedPsiElementBase<?>> t : refToPsi) {
        if (t != null && t.get() != null) {
          return true;
        }
      }
    }
    return false;
  }

  /** Returns {@code true} if PSI elements are currently tracked via {@link SpineRef} weak references. */
  boolean useSpineRefs() {
    return myRefToPsi != null;
  }

  /**
   * Registers all stubbed PSI elements from the given {@code spine} into {@link #myRefToPsi} and
   * sets each element's substrate to a {@link SpineRef} so it can lazily resolve against either
   * stubs or AST.
   */
  @NotNull FileTrees switchToSpineRefs(@NotNull List<PsiElement> spine) {
    Reference<StubBasedPsiElementBase<?>>[] refToPsi = myRefToPsi;
    if (refToPsi == null) {
      //noinspection unchecked
      refToPsi = new Reference[spine.size()];
    }

    try {
      for (int i = firstNonFilePsiIndex; i < refToPsi.length; i++) {
        StubBasedPsiElementBase<?> psi = (StubBasedPsiElementBase<?>)Objects.requireNonNull(spine.get(i));
        psi.setSubstrateRef(new SpineRef(myFile, i));
        StubBasedPsiElementBase<?> existing = dereference(refToPsi[i]);
        if (existing != null) {
          assert existing == psi : "Duplicate PSI found";
        }
        else {
          refToPsi[i] = new WeakReference<>(psi);
        }
      }
      return new FileTrees(myFile, myStub, myTreeElementPointer, refToPsi);
    }
    catch (Throwable e) {
      throw new RuntimeException("Exceptions aren't allowed here", e);
      // otherwise, e.g. in case of PCE, we'd remain with PSI having SpineRef's but not registered in any "myRefToPsi"
      // and so that PSI wouldn't be updated on AST change
    }
  }

  /**
   * Detaches the stub tree and invalidates all spine-tracked PSI.
   * Called when the file's AST is about to be mutated (e.g. after a reparse), so stale stubs
   * must not be reachable.
   *
   * @param reason diagnostic string stored on the invalidated stub for debugging
   * @return a new {@code FileTrees} with no stubs and no spine refs
   */
  @NotNull FileTrees clearStub(@NotNull String reason) {
    StubTree stubHolder = derefStub();
    if (stubHolder != null) {
      ((PsiFileStubImpl<?>)stubHolder.getRoot()).clearPsi(reason);
    }

    if (myRefToPsi != null) {
      DebugUtil.performPsiModification("clearStub", () -> forEachCachedPsi(psi -> {
        DebugUtil.onInvalidated(psi);
        psi.setSubstrateRef(SubstrateRef.createInvalidRef(psi));
      }));
    }

    return new FileTrees(myFile, null, myTreeElementPointer, null);
  }

  /**
   * Produces a new {@code FileTrees} with the given AST attached. If stubs are also present,
   * sync PSI with stubs so that both trees share the same PSI objects (preferring stub-originated PSI).
   *
   * @param ast supplier for the newly parsed {@link FileElement}
   * @see PsiFileImpl#loadTreeElement()
   */
  @NotNull FileTrees withAst(@NotNull Supplier<? extends FileElement> ast) throws StubTreeLoader.StubTreeAndIndexUnmatchCoarseException {
    return new FileTrees(myFile, myStub, ast, myRefToPsi).syncPsiWithStub(derefStub(), ast.get(), true);
  }

  /**
   * Produces a new {@code FileTrees} with the given stub tree attached. If AST is also present,
   * sync PSI with stubs so that both trees share the same PSI objects (preferring AST-originated PSI).
   *
   * @param stub the freshly loaded or built stub tree
   * @param ast  the current AST root, or {@code null} if none is loaded
   * @see PsiFileImpl#setStubTree
   */
  @NotNull FileTrees withStub(@NotNull StubTree stub,
                              @Nullable FileElement ast) throws StubTreeLoader.StubTreeAndIndexUnmatchCoarseException {
    assert derefTreeElement() == ast;
    return new FileTrees(myFile, new SoftReference<>(stub), myTreeElementPointer, myRefToPsi)
      .syncPsiWithStub(stub, ast, false);
  }

  /** Creates an initial {@code FileTrees} with no stub tree and an optional AST root. */
  @NotNull
  static FileTrees noStub(@Nullable FileElement ast, @NotNull PsiFileImpl file) {
    return new FileTrees(file, null, ast == null ? null : () -> ast, null);
  }

  /**
   * Ensures {@link #myRefToPsi}, stubs, and AST all have the same PSI at corresponding indices.
   * In case several sources already have PSI (e.g. created during AST parsing), overwrites them with the "correct" one,
   * which is taken from {@link #myRefToPsi} if exists, otherwise from either stubs or AST depending on {@code takePsiFromStubs}.
   */
  private @NotNull FileTrees syncPsiWithStub(@Nullable StubTree stubTree,
                                             @Nullable FileElement astRoot,
                                             boolean takePsiFromStubs)
    throws StubTreeLoader.StubTreeAndIndexUnmatchCoarseException {
    assert stubTree != null || astRoot != null;

    if ((stubTree == null || astRoot == null) && !hasCachedPsi()) {
      // there's only one source of PSI, nothing to reconcile
      return new FileTrees(myFile, myStub, myTreeElementPointer, null);
    }

    List<StubElement<?>> stubList = stubTree == null ? null : stubTree.getPlainList();
    List<CompositeElement> nodeList = astRoot == null ? null : astRoot.getStubbedSpine().getSpineNodes();
    List<PsiElement> srcSpine = stubList == null || nodeList == null
                                ? null
                                : getAllSpinePsi(takePsiFromStubs ? stubTree.getSpine() : astRoot.getStubbedSpine());

    try {
      return DebugUtil.performPsiModification("reconcilePsi", () -> {
        if (myRefToPsi != null) {
          assert myRefToPsi.length == (stubList != null ? stubList.size() : nodeList.size())
            : "Cached PSI count doesn't match actual one. " +
              "myRefToPsi.length=" + myRefToPsi.length + ", " +
              "stubList.size=" + (stubList == null ? "null" : stubList.size()) + ", " +
              "nodeList.size=" + (nodeList == null ? "null" : nodeList.size());
          bindSubstratesToCachedPsi(stubList, nodeList);
        }

        if (stubList != null && nodeList != null) {
          assert stubList.size() == nodeList.size() : "Stub count (" + stubList.size() + ") doesn't match " +
                                                      "stubbed node length (" + nodeList.size() + ")";

          FileTrees result = switchToSpineRefs(srcSpine);
          bindStubsWithAst(srcSpine, stubList, nodeList, takePsiFromStubs);
          return result;
        }
        return this;
      });
    }
    catch (Throwable e) {
      myFile.clearContent(PsiFileImpl.STUB_PSI_MISMATCH);
      myFile.rebuildStub();
      throw StubTreeLoader.getInstance().createCoarseExceptionStubTreeAndIndexDoNotMatch(stubTree, myFile, e, FileTreesPsiReconciliation);
    }
  }

  /**
   * {@link StubbedSpine#getStubPsi(int)} may throw {@link com.intellij.openapi.progress.ProcessCanceledException},
   * so shouldn't be invoked in the middle of a mutating operation to avoid leaving inconsistent state.
   * So we obtain PSI all at once in advance.
   */
  static @NotNull List<PsiElement> getAllSpinePsi(@NotNull StubbedSpine spine) {
    return IntStream.range(0, spine.getStubCount()).mapToObj(index -> spine.getStubPsi(index)).collect(Collectors.toList());
  }

  /**
   * For every previously cached PSI element in {@link #myRefToPsi}, injects it into the
   * corresponding stub and/or AST node so that those trees return the same PSI object.
   */
  private void bindSubstratesToCachedPsi(@Nullable List<StubElement<?>> stubList,
                                         @Nullable List<? extends CompositeElement> nodeList) {
    assert myRefToPsi != null;
    for (int i = firstNonFilePsiIndex; i < myRefToPsi.length; i++) {
      StubBasedPsiElementBase<?> cachedPsi = dereference(myRefToPsi[i]);
      if (cachedPsi != null) {
        if (stubList != null) {
          // noinspection unchecked
          ((StubBase<StubBasedPsiElementBase<?>>)stubList.get(i)).setPsi(cachedPsi);
        }
        if (nodeList != null) {
          nodeList.get(i).setPsi(cachedPsi);
        }
      }
    }
  }

  /**
   * Cross-binds stubs and AST nodes: for each spine position, sets the PSI from the
   * "source" tree onto the "target" tree so both trees return the same PSI instance.
   *
   * @param takePsiFromStubs if {@code true}, PSI originates from stubs and is set onto AST nodes;
   *                         if {@code false}, PSI originates from AST nodes and is set onto stubs
   */
  private static void bindStubsWithAst(@NotNull List<? extends PsiElement> srcSpine,
                                       @NotNull List<? extends StubElement<?>> stubList,
                                       @NotNull List<? extends CompositeElement> nodeList,
                                       boolean takePsiFromStubs) {
    for (int i = firstNonFilePsiIndex; i < stubList.size(); i++) {
      StubElement<?> stub = stubList.get(i);
      CompositeElement node = nodeList.get(i);
      assert stub.getElementType() == node.getElementType() : "Stub type mismatch: " + stub.getElementType() + "!=" + node.getElementType() + " in #" + node.getElementType().getLanguage();

      PsiElement psi = Objects.requireNonNull(srcSpine.get(i));
      if (takePsiFromStubs) {
        node.setPsi(psi);
      }
      else {
        //noinspection unchecked
        ((StubBase<PsiElement>)stub).setPsi(psi);
      }
    }
  }

  @Override
  public String toString() {
    return "FileTrees{" +
           "stub=" + (myStub == null ? "noRef" : derefStub()) +
           ", AST=" + (myTreeElementPointer == null ? "noRef" : derefTreeElement()) +
           ", useSpineRefs=" + useSpineRefs() +
           '}';
  }
}
