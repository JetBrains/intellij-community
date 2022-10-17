// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.stubs.*;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class FileTrees {
  private static final Logger LOG = Logger.getInstance(FileTrees.class);
  private static final int firstNonFilePsiIndex = 1;
  private final PsiFileImpl myFile;
  private final Reference<StubTree> myStub;
  private final Supplier<? extends FileElement> myTreeElementPointer; // SoftReference/WeakReference to ASTNode or a strong reference to a tree if the file is a DummyHolder

  /** Keeps references to all alive stubbed PSI (using {@link SpineRef}) to ensure PSI identity is preserved after AST/stubs are gc-ed and reloaded */
  private final Reference<StubBasedPsiElementBase<?>> @Nullable [] myRefToPsi;

  private FileTrees(@NotNull PsiFileImpl file,
                    @Nullable Reference<StubTree> stub,
                    @Nullable Supplier<? extends FileElement> ast,
                    Reference<StubBasedPsiElementBase<?>> @Nullable [] refToPsi) {
    myFile = file;
    myStub = stub;
    myTreeElementPointer = ast;
    myRefToPsi = refToPsi;
  }

  @Nullable
  StubTree derefStub() {
    return SoftReference.dereference(myStub);
  }

  @Nullable
  FileElement derefTreeElement() {
    return SoftReference.deref(myTreeElementPointer);
  }

  FileTrees switchToStrongRefs() {
    if (myRefToPsi == null) return this;

    forEachCachedPsi(psi -> {
      ASTNode node = psi.getNode();
      LOG.assertTrue(node.getPsi() == psi);
      psi.setSubstrateRef(SubstrateRef.createAstStrongRef(node));
    });

    return new FileTrees(myFile, myStub, myTreeElementPointer, null);
  }

  private void forEachCachedPsi(Consumer<? super StubBasedPsiElementBase<?>> consumer) {
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

  boolean useSpineRefs() {
    return myRefToPsi != null;
  }

  FileTrees switchToSpineRefs(@NotNull List<PsiElement> spine) {
    Reference<StubBasedPsiElementBase<?>>[] refToPsi = myRefToPsi;
    if (refToPsi == null) {
      //noinspection unchecked
      refToPsi = new Reference[spine.size()];
    }

    try {
      for (int i = firstNonFilePsiIndex; i < refToPsi.length; i++) {
        StubBasedPsiElementBase<?> psi = (StubBasedPsiElementBase<?>)Objects.requireNonNull(spine.get(i));
        psi.setSubstrateRef(new SpineRef(myFile, i));
        StubBasedPsiElementBase<?> existing = SoftReference.dereference(refToPsi[i]);
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

  FileTrees clearStub(@NotNull String reason) {
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

  FileTrees withAst(@NotNull Supplier<? extends FileElement> ast) {
    return new FileTrees(myFile, myStub, ast, myRefToPsi).reconcilePsi(derefStub(), ast.get(), true);
  }

  FileTrees withStub(@NotNull StubTree stub, @Nullable FileElement ast) {
    assert derefTreeElement() == ast;
    return new FileTrees(myFile, new SoftReference<>(stub), myTreeElementPointer, myRefToPsi)
      .reconcilePsi(stub, ast, false);
  }

  static FileTrees noStub(@Nullable FileElement ast, @NotNull PsiFileImpl file) {
    return new FileTrees(file, null, () -> ast, null);
  }

  /**
   * Ensures {@link #myRefToPsi}, stubs and AST all have the same PSI at corresponding indices.
   * In case several sources already have PSI (e.g. created during AST parsing), overwrites them with the "correct" one,
   * which is taken from {@link #myRefToPsi} if exists, otherwise from either stubs or AST depending on {@code takePsiFromStubs}.
   */
  private FileTrees reconcilePsi(@Nullable StubTree stubTree, @Nullable FileElement astRoot, boolean takePsiFromStubs) {
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
          assert myRefToPsi.length == (stubList != null ? stubList.size() : nodeList.size()) : "Cached PSI count doesn't match actual one";
          bindSubstratesToCachedPsi(stubList, nodeList);
        }

        if (stubList != null && nodeList != null) {
          assert stubList.size() == nodeList.size() : "Stub count doesn't match stubbed node length";

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
      throw StubTreeLoader.getInstance().stubTreeAndIndexDoNotMatch(stubTree, myFile, e);
    }
  }

  /**
   * {@link StubbedSpine#getStubPsi(int)} may throw {@link com.intellij.openapi.progress.ProcessCanceledException},
   * so shouldn't be invoked in the middle of a mutating operation to avoid leaving inconsistent state.
   * So we obtain PSI all at once in advance.
   */
  static List<PsiElement> getAllSpinePsi(@NotNull StubbedSpine spine) {
    return IntStream.range(0, spine.getStubCount()).mapToObj(spine::getStubPsi).collect(Collectors.toList());
  }

  private void bindSubstratesToCachedPsi(List<StubElement<?>> stubList, List<? extends CompositeElement> nodeList) {
    assert myRefToPsi != null;
    for (int i = firstNonFilePsiIndex; i < myRefToPsi.length; i++) {
      StubBasedPsiElementBase<?> cachedPsi = SoftReference.dereference(myRefToPsi[i]);
      if (cachedPsi != null) {
        if (stubList != null) {
          // noinspection unchecked
          ((StubBase)stubList.get(i)).setPsi(cachedPsi);
        }
        if (nodeList != null) {
          nodeList.get(i).setPsi(cachedPsi);
        }
      }
    }
  }

  private static void bindStubsWithAst(@NotNull List<? extends PsiElement> srcSpine, List<? extends StubElement<?>> stubList, List<? extends CompositeElement> nodeList, boolean takePsiFromStubs) {
    for (int i = firstNonFilePsiIndex; i < stubList.size(); i++) {
      StubElement<?> stub = stubList.get(i);
      CompositeElement node = nodeList.get(i);
      assert stub.getStubType() == node.getElementType() : "Stub type mismatch: " + stub.getStubType() + "!=" + node.getElementType() + " in #" + node.getElementType().getLanguage();

      PsiElement psi = Objects.requireNonNull(srcSpine.get(i));
      if (takePsiFromStubs) {
        node.setPsi(psi);
      }
      else {
        //noinspection unchecked
        ((StubBase)stub).setPsi(psi);
      }
    }
  }

  @Override
  public String toString() {
    return "FileTrees{" +
           "stub=" + (myStub == null ? "noRef" : derefStub()) +
           ", AST=" + (myTreeElementPointer == null ? "noRef" : derefTreeElement()) +
           ", useSpineRefs=" + useSpineRefs() +
           '}' ;
  }
}
