/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.tree;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.SubstrateRef;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.stubs.StubTree;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * A lightweight object representing a chain of node indices (among all lazy-parseable and stub-based elements)
 * allowing to restore a specific node after it's been garbage-collected and recreated.
 *
 * @author peter
 */
public abstract class AstPath extends SubstrateRef {
  @SuppressWarnings("ConstantForZeroLengthArrayAllocation") // need a different instance to distinguish removed children for diagnostics
  private static final CompositeElement[] REMOVED_PATH_CHILDREN = new CompositeElement[0];
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.AstPath");
  private static final Key<CompositeElement[]> PATH_CHILDREN = Key.create("PATH_CHILDREN");
  private static final Key<AstPath> NODE_PATH = Key.create("NODE_PATH");

  @NotNull
  public abstract PsiFileImpl getContainingFile();

  @NotNull
  public abstract CompositeElement getNode();

  @Override
  public boolean isValid() {
    return getContainingFile().isValid();
  }

  protected abstract int getDepth();

  @Nullable
  @Override
  public Stub getStub(int stubIndex) {
    if (stubIndex < 0) return null;

    StubTree stubTree = getFileStubTree();
    return stubTree == null ? null : stubTree.getPlainList().get(stubIndex);
  }

  @Nullable
  protected abstract StubTree getFileStubTree();

  @Nullable
  @Override
  public Stub getGreenStub(int stubIndex) {
    if (stubIndex < 0) return null;

    StubTree stubTree = getContainingFile().getGreenStubTree();
    return stubTree == null ? null : stubTree.getPlainList().get(stubIndex);
  }

  @Nullable
  public static AstPath getNodePath(@NotNull CompositeElement node) {
    if (node instanceof FileElement) {
      PsiElement psi = node.getCachedPsi();
      if (!(psi instanceof PsiFileImpl)) return null;

      PsiFileImpl file = (PsiFileImpl)psi;
      if (!(file.getVirtualFile() instanceof VirtualFileWithId) || file.getElementTypeForStubBuilder() == null) {
        return null;
      }
      return new RootPath(file);
    }

    return node.getUserData(NODE_PATH);
  }

  static void cacheNodePaths(@NotNull LazyParseableElement parent, @Nullable TreeElement child, @Nullable AstPath parentPath) {
    if (parentPath == null) {
      return;
    }

    final int depth = parentPath.getDepth() + 1;

    final List<CompositeElement> children = ContainerUtil.newArrayList();
    while (child != null) {
      child.acceptTree(new RecursiveTreeElementWalkingVisitor(false) {
        @Override
        public void visitComposite(CompositeElement composite) {
          boolean lazy = composite instanceof LazyParseableElement;
          if (lazy || composite.getElementType() instanceof IStubElementType) {
            int index = children.size();
            composite.putUserData(NODE_PATH, depth % 4 == 0 ? new MilestoneChildPath(parentPath, index, depth) : new ChildPath(parentPath, index));
            children.add(composite);
          }

          if (!lazy) {
            super.visitComposite(composite);
          }
        }
      });
      child = child.getTreeNext();
    }

    parent.putUserData(PATH_CHILDREN, children.isEmpty() ? CompositeElement.EMPTY_ARRAY : children.toArray(CompositeElement.EMPTY_ARRAY));

    for (CompositeElement each : children) {
      if (each instanceof LazyParseableElement && ((LazyParseableElement)each).isParsed()) {
        cacheNodePaths((LazyParseableElement)each, each.getFirstChildNode(), getNodePath(each));
      }
    }
  }

  public static void invalidatePaths(@NotNull LazyParseableElement scope) {
    CompositeElement[] children = scope.getUserData(PATH_CHILDREN);
    if (children == null) return;

    scope.putUserData(PATH_CHILDREN, REMOVED_PATH_CHILDREN);
    for (CompositeElement child : children) {
      child.putUserData(NODE_PATH, null);
      assertConsistency(child.getCachedPsi());
      if (child instanceof LazyParseableElement) {
        invalidatePaths((LazyParseableElement)child);
      }
    }
  }

  private static void assertConsistency(PsiElement cachedPsi) {
    if (cachedPsi instanceof StubBasedPsiElementBase &&
        ((StubBasedPsiElementBase)cachedPsi).getSubstrateRef() instanceof AstPath) {
      LOG.error("Expected strong reference at " + cachedPsi +
                " of " + cachedPsi.getClass() +
                " and " + ((StubBasedPsiElementBase)cachedPsi).getElementType());
    }
  }

  private static class ChildPath extends AstPath {
    private final AstPath myParent;
    private final int myIndex;

    ChildPath(@NotNull AstPath parent, int index) {
      myParent = parent;
      myIndex = index;
    }

    @NotNull
    @Override
    public PsiFileImpl getContainingFile() {
      return myParent.getContainingFile();
    }

    @NotNull
    @Override
    public CompositeElement getNode() {
      CompositeElement parentNode = myParent.getNode();
      //noinspection ResultOfMethodCallIgnored
      parentNode.getFirstChildNode(); // expand chameleons, populate PATH_CHILDREN array
      CompositeElement[] children = parentNode.getUserData(PATH_CHILDREN);
      boolean removed = children == REMOVED_PATH_CHILDREN;
      if (children == null || removed) {
        throw reportMissingChildren(parentNode, removed);
      }
      if (myIndex >= children.length) {
        throw new AssertionError(myIndex + " >= " + children.length + "; " + parentNode + " of " + parentNode.getClass());
      }
      return children[myIndex];
    }

    private AssertionError reportMissingChildren(CompositeElement parentNode, boolean removed) {
      String message = "No path children in " + parentNode + " of " + parentNode.getClass() + "; removed=" + removed;

      PsiFileImpl file = getContainingFile();
      message += "\n  file: " + file + " of " + file.getClass() + "; physical=" + file.isPhysical() + "; useStrongRefs=" + file.useStrongRefs();

      FileElement fileElement = file.getTreeElement();
      message += "\n  ast=" + fileElement;
      if (fileElement != null) {
        CompositeElement[] rootChildren = fileElement.getUserData(PATH_CHILDREN);
        message += "; root.children=" + (rootChildren == REMOVED_PATH_CHILDREN ? "removed" : rootChildren == null ? "null" : "nonNull");
      }
      return new AssertionError(message);
    }

    @Override
    protected int getDepth() {
      return 1 + myParent.getDepth();
    }

    @Override
    protected StubTree getFileStubTree() {
      return myParent.getFileStubTree();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ChildPath)) return false;

      ChildPath path = (ChildPath)o;
      return myIndex == path.myIndex && myParent.equals(path.myParent);
    }

    @Override
    public int hashCode() {
      return 31 * myParent.hashCode() + myIndex;
    }
  }

  private static class MilestoneChildPath extends ChildPath {
    private final int myDepth;
    private final PsiFileImpl myFile;
    private volatile WeakReference<CompositeElement> myNode;

    MilestoneChildPath(@NotNull AstPath parent, int index, int depth) {
      super(parent, index);
      myDepth = depth;
      myFile = parent.getContainingFile();
    }

    @NotNull
    @Override
    public CompositeElement getNode() {
      CompositeElement node = SoftReference.dereference(myNode);
      if (node == null) {
        node = super.getNode();
        if (myFile.mayCacheAst()) {
          myNode = new WeakReference<>(node);
        }
      }
      return node;
    }

    @Override
    protected StubTree getFileStubTree() {
      return SoftReference.dereference(myNode) == null ? myFile.getStubTree() : null;
    }

    @NotNull
    @Override
    public PsiFileImpl getContainingFile() {
      return myFile;
    }

    @Override
    protected int getDepth() {
      return myDepth;
    }
  }

  private static class RootPath extends AstPath {
    private final PsiFileImpl myFile;

    RootPath(@NotNull PsiFileImpl file) {
      myFile = file;
    }

    @Override
    public boolean equals(Object o) {
      return this == o || o instanceof RootPath && myFile.equals(((RootPath)o).myFile);
    }

    @Override
    public int hashCode() {
      return myFile.hashCode();
    }

    @NotNull
    @Override
    public PsiFileImpl getContainingFile() {
      return myFile;
    }

    @NotNull
    @Override
    public CompositeElement getNode() {
      return myFile.calcTreeElement();
    }

    @Override
    protected int getDepth() {
      return 0;
    }

    @Override
    protected StubTree getFileStubTree() {
      return myFile.getStubTree();
    }
  }

}
