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
package com.intellij.psi.impl.source;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Getter;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * @author peter
 */
final class FileTrees {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.FileTrees");
  private static final int firstNonFilePsiIndex = 1;
  private final PsiFileImpl myFile;
  private final Reference<StubTree> myStub;
  private final Getter<FileElement> myTreeElementPointer; // SoftReference/WeakReference to ASTNode or a strong reference to a tree if the file is a DummyHolder
  
  /** Keeps references to all alive stubbed PSI (using {@link SpineRef}) to ensure PSI identity is preserved after AST/stubs are gc-ed and reloaded */
  @Nullable private final List<Reference<StubBasedPsiElementBase>> myRefToPsi;

  private FileTrees(@NotNull PsiFileImpl file,
                    @Nullable Reference<StubTree> stub,
                    @Nullable Getter<FileElement> ast,
                    @Nullable List<Reference<StubBasedPsiElementBase>> refToPsi) {
    this.myFile = file;
    this.myStub = stub;
    this.myTreeElementPointer = ast;
    this.myRefToPsi = refToPsi;
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

    getAllCachedPsi(myRefToPsi).forEach(psi -> {
      ASTNode node = psi.getNode();
      LOG.assertTrue(node.getPsi() == psi);
      psi.setSubstrateRef(SubstrateRef.createAstStrongRef(node));
    });
    
    return new FileTrees(myFile, myStub, myTreeElementPointer, null);
  }

  private static Stream<StubBasedPsiElementBase> getAllCachedPsi(@NotNull List<Reference<StubBasedPsiElementBase>> refToPsi) {
    return refToPsi.stream().map(SoftReference::dereference).filter(Objects::nonNull);
  }

  boolean useSpineRefs() {
    return myRefToPsi != null;
  }

  FileTrees switchToSpineRefs(@NotNull List<PsiElement> spine) {
    List<Reference<StubBasedPsiElementBase>> refToPsi = myRefToPsi;
    if (refToPsi == null) refToPsi = new ArrayList<>(Collections.nCopies(spine.size(), null));

    try {
      for (int i = firstNonFilePsiIndex; i < refToPsi.size(); i++) {
        StubBasedPsiElementBase psi = (StubBasedPsiElementBase)Objects.requireNonNull(spine.get(i));
        psi.setSubstrateRef(new SpineRef(myFile, i));
        StubBasedPsiElementBase existing = SoftReference.dereference(refToPsi.get(i));
        if (existing != null) {
          assert existing == psi : "Duplicate PSI found";
        } else {
          refToPsi.set(i, new WeakReference<>(psi));
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
      DebugUtil.performPsiModification("clearStub", () -> getAllCachedPsi(myRefToPsi).forEach(psi -> {
        DebugUtil.onInvalidated(psi);
        psi.setSubstrateRef(SubstrateRef.createInvalidRef(psi));
      }));
    }

    return new FileTrees(myFile, null, myTreeElementPointer, null);
  }

  FileTrees withAst(@NotNull Getter<FileElement> ast) {
    return new FileTrees(myFile, myStub, ast, myRefToPsi).reconcilePsi(derefStub(), ast.get(), true);
  }

  FileTrees withStub(@NotNull StubTree stub, @Nullable FileElement ast) {
    assert derefTreeElement() == ast;
    return new FileTrees(myFile, new SoftReference<>(stub), myTreeElementPointer, myRefToPsi)
      .reconcilePsi(stub, ast, false);
  }

  static FileTrees noStub(@Nullable FileElement ast, @NotNull PsiFileImpl file) {
    return new FileTrees(file, null, ast, null);
  }

  /**
   * Ensures {@link #myRefToPsi}, stubs and AST all have the same PSI at corresponding indices.
   * In case several sources already have PSI (e.g. created during AST parsing), overwrites them with the "correct" one,
   * which is taken from {@link #myRefToPsi} if exists, otherwise from either stubs or AST depending on {@code takePsiFromStubs}.
   */
  private FileTrees reconcilePsi(@Nullable StubTree stubTree, @Nullable FileElement astRoot, boolean takePsiFromStubs) {
    assert stubTree != null || astRoot != null;

    if ((stubTree == null || astRoot == null) && (myRefToPsi == null || !getAllCachedPsi(myRefToPsi).findFirst().isPresent())) {
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
          assert myRefToPsi.size() == (stubList != null ? stubList.size() : nodeList.size()) : "Cached PSI count doesn't match actual one";
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
      LOG.error(e);
      myFile.clearContent(PsiFileImpl.STUB_PSI_MISMATCH);
      myFile.rebuildStub();
      throw StubTreeLoader.getInstance().stubTreeAndIndexDoNotMatch(e.getMessage(), stubTree, myFile);
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

  private void bindSubstratesToCachedPsi(List<StubElement<?>> stubList, List<CompositeElement> nodeList) {
    assert myRefToPsi != null;
    for (int i = firstNonFilePsiIndex; i < myRefToPsi.size(); i++) {
      StubBasedPsiElementBase cachedPsi = SoftReference.dereference(myRefToPsi.get(i));
      if (cachedPsi != null) {
        if (stubList != null) {
          //noinspection unchecked
          ((StubBase)stubList.get(i)).setPsi(cachedPsi);
        }
        if (nodeList != null) {
          nodeList.get(i).setPsi(cachedPsi);
        }
      }
    }
  }

  private static void bindStubsWithAst(@NotNull List<PsiElement> srcSpine, List<StubElement<?>> stubList, List<CompositeElement> nodeList, boolean takePsiFromStubs) {
    for (int i = firstNonFilePsiIndex; i < stubList.size(); i++) {
      StubElement<?> stub = stubList.get(i);
      CompositeElement node = nodeList.get(i);
      assert stub.getStubType() == node.getElementType() : "Stub type mismatch";

      PsiElement psi = Objects.requireNonNull(srcSpine.get(i));
      if (takePsiFromStubs) {
        node.setPsi(psi);
      } else {
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
