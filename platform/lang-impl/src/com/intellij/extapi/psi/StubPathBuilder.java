/*
 * @author max
 */
package com.intellij.extapi.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.stubs.*;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class StubPathBuilder {
  private StubPathBuilder() {
  }

  public static StubPath build(StubBasedPsiElement psi) {
    if (psi instanceof PsiFile) {
      return null;
    }

    final StubElement liveStub = psi.getStub();
    if (liveStub != null) {
      return build(liveStub);
    }

    return buildForPsi(psi, ((PsiFileImpl)psi.getContainingFile()).calcStubTree()).getFirst();
  }


  public static StubPath build(StubElement stub) {
    if (stub instanceof PsiFileStub || stub == null) {
      return null;
    }

    final IStubElementType type = stub.getStubType();
    return new StubPath(build(stub.getParentStub()), type.getId(stub), type);
  }

  private static Pair<StubPath, StubElement> buildForPsi(PsiElement psi, StubTree tree) {
    if (psi instanceof PsiFile) {
      return new Pair<StubPath, StubElement>(null, tree.getRoot());
    }

    if (psi instanceof StubBasedPsiElement) {
      final IStubElementType type = ((StubBasedPsiElement)psi).getElementType();
      if (type.shouldCreateStub(psi.getNode())) {
        final Pair<StubPath, StubElement> parentPair = buildForPsi(psi.getParent(), tree);

        final StubElement parentStub = parentPair.getSecond();
        final List<StubElement> childrenStubs = parentStub.getChildrenStubs();
        for (StubElement childStub : childrenStubs) {
          if (childStub.getPsi() == psi) {
            final IStubElementType type1 = childStub.getStubType();
            return new Pair<StubPath, StubElement>(new StubPath(parentPair.getFirst(), type1.getId(childStub), type1), childStub);
          }
        }

        return new Pair<StubPath, StubElement>(null, null);
      }
    }

    return buildForPsi(psi.getParent(), tree);
  }

  @Nullable
  public static PsiElement resolve(PsiFile file, StubPath path) {
    PsiFileImpl fileImpl = (PsiFileImpl)file;
    StubTree tree = fileImpl.getStubTree();

    boolean foreign = (tree == null);
    if (foreign) {
      tree = fileImpl.calcStubTree();
    }

    StubElement stub = resolveStub(tree, path);

    if (foreign) {
      final PsiElement cachedPsi = ((StubBase)stub).getCachedPsi();
      if (cachedPsi != null) return cachedPsi;

      final ASTNode ast = fileImpl.findTreeForStub(tree, stub);
      return ast != null ? ast.getPsi() : null;
    }
    else {
      return stub != null ? stub.getPsi() : null;
    }
  }

  @Nullable
  public static StubElement resolveStub(StubTree tree, StubPath path) {
    if (path == null) {
      return tree.getRoot();
    }

    StubElement parentStub = resolveStub(tree, path.getParentPath());
    if (parentStub == null) {
      return null;
    }

    final String id = path.getId();
    final IStubElementType type = (IStubElementType)path.getType();
    final List<StubElement> children = parentStub.getChildrenStubs();

    if (id.startsWith("#")) {
      int count = Integer.parseInt(id.substring(1));

      for (StubElement child : children) {
        if (child.getStubType() == type) {
          count--;
          if (count == 0) {
            return child;
          }
        }
      }
      return null;
    }

    for (StubElement child : children) {
      if (child.getStubType() == type && id.equals(type.getId(child))) {
        return child;
      }
    }

    return null;
  }
}