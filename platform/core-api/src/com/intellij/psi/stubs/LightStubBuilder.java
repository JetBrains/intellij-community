/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.stubs;

import com.intellij.lang.*;
import com.intellij.openapi.diagnostic.LogUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.ILightStubFileElementType;
import com.intellij.util.CharTable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import gnu.trove.TIntStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LightStubBuilder implements StubBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.LightStubBuilder");

  @Override
  public StubElement buildStubTree(@NotNull PsiFile file) {
    final FileType fileType = file.getFileType();
    if (!(fileType instanceof LanguageFileType)) {
      LOG.error("File is not of LanguageFileType: " + fileType + ", " + file);
      return null;
    }

    final Language language = ((LanguageFileType)fileType).getLanguage();
    final IFileElementType contentType = LanguageParserDefinitions.INSTANCE.forLanguage(language).getFileNodeType();
    if (!(contentType instanceof ILightStubFileElementType)) {
      LOG.error("File is not of ILightStubFileElementType: " + contentType + ", " + file);
      return null;
    }

    final FileASTNode node = file.getNode();
    assert node != null : file;

    final LighterAST tree;
    if (!node.isParsed()) {
      final ILightStubFileElementType<?> type = (ILightStubFileElementType)contentType;
      tree = new FCTSBackedLighterAST(node.getCharTable(), type.parseContentsLight(node));
    }
    else {
      tree = new TreeBackedLighterAST(node);
    }

    final StubElement rootStub = createStubForFile(file, tree);
    buildStubTree(tree, tree.getRoot(), rootStub);
    return rootStub;
  }

  @NotNull
  @SuppressWarnings("unchecked")
  protected StubElement createStubForFile(@NotNull PsiFile file, @NotNull LighterAST tree) {
    return new PsiFileStubImpl(file);
  }

  protected void buildStubTree(@NotNull LighterAST tree, @NotNull LighterASTNode root, @NotNull StubElement rootStub) {
    final Stack<LighterASTNode> parents = new Stack<LighterASTNode>();
    final TIntStack childNumbers = new TIntStack();
    final Stack<List<LighterASTNode>> kinderGarden = new Stack<List<LighterASTNode>>();
    final Stack<StubElement> parentStubs = new Stack<StubElement>();

    LighterASTNode parent = null;
    LighterASTNode element = root;
    List<LighterASTNode> children = null;
    int childNumber = 0;
    StubElement parentStub = rootStub;

    nextElement:
    while (element != null) {
      final StubElement stub = createStub(tree, element, parentStub);

      if (parent == null || !skipNode(tree, parent, element)) {
        final List<LighterASTNode> kids = tree.getChildren(element);
        if (!kids.isEmpty()) {
          if (parent != null) {
            parents.push(parent);
            childNumbers.push(childNumber);
            kinderGarden.push(children);
            parentStubs.push(parentStub);
          }
          parent = element;
          element = (children = kids).get(childNumber = 0);
          parentStub = stub;
          if (!skipNode(tree, parent, element)) continue nextElement;
        }
      }

      while (children != null && ++childNumber < children.size()) {
        element = children.get(childNumber);
        if (!skipNode(tree, parent, element)) continue nextElement;
      }

      element = null;
      while (!parents.isEmpty()) {
        parent = parents.pop();
        childNumber = childNumbers.pop();
        children = kinderGarden.pop();
        parentStub = parentStubs.pop();
        while (++childNumber < children.size()) {
          element = children.get(childNumber);
          if (!skipNode(tree, parent, element)) continue nextElement;
        }
        element = null;
      }
    }
  }

  @SuppressWarnings({"MethodMayBeStatic"})
  protected StubElement createStub(final LighterAST tree, final LighterASTNode element, final StubElement parentStub) {
    final IElementType elementType = element.getTokenType();
    if (elementType instanceof IStubElementType) {
      if (elementType instanceof ILightStubElementType) {
        final ILightStubElementType lightElementType = (ILightStubElementType)elementType;
        if (lightElementType.shouldCreateStub(tree, element, parentStub)) {
          return lightElementType.createStub(tree, element, parentStub);
        }
      }
      else {
        LOG.error("Element is not of ILightStubElementType: " + LogUtil.objectAndClass(elementType) + ", " + element);
      }
    }

    return parentStub;
  }

  private boolean skipNode(@NotNull LighterAST tree, @NotNull LighterASTNode parent, @NotNull LighterASTNode node) {
    if (tree instanceof TreeBackedLighterAST) {
      return skipChildProcessingWhenBuildingStubs(((TreeBackedLighterAST)tree).unwrap(parent), ((TreeBackedLighterAST)tree).unwrap(node));
    }
    else {
      return skipChildProcessingWhenBuildingStubs(tree, parent, node);
    }
  }

  /**
   * Note to implementers: always keep in sync with {@linkplain #skipChildProcessingWhenBuildingStubs(LighterAST, LighterASTNode, LighterASTNode)}.
   * todo[r.sh] move to interface (IDEA 13)
   */
  @SuppressWarnings("deprecation")
  public boolean skipChildProcessingWhenBuildingStubs(@NotNull ASTNode parent, @NotNull ASTNode node) {
    return skipChildProcessingWhenBuildingStubs(parent, node.getElementType());
  }

  /**
   * Note to implementers: always keep in sync with {@linkplain #skipChildProcessingWhenBuildingStubs(ASTNode, ASTNode)}.
   */
  @SuppressWarnings("deprecation")
  protected boolean skipChildProcessingWhenBuildingStubs(@NotNull LighterAST tree, @NotNull LighterASTNode parent, @NotNull LighterASTNode node) {
    return skipChildProcessingWhenBuildingStubs(parent.getTokenType(), node.getTokenType());
  }

  /** @deprecated override {@linkplain #skipChildProcessingWhenBuildingStubs(ASTNode, ASTNode)} (to remove in IDEA 13) */
  @SuppressWarnings("deprecation")
  @Override
  public final boolean skipChildProcessingWhenBuildingStubs(@Nullable ASTNode parent, IElementType childType) {
    return skipChildProcessingWhenBuildingStubs(parent != null ? parent.getElementType() : null, childType);
  }

  /** @deprecated override {@linkplain #skipChildProcessingWhenBuildingStubs(LighterAST, LighterASTNode, LighterASTNode)} (to remove in IDEA 13) */
  @SuppressWarnings("unused")
  public boolean skipChildProcessingWhenBuildingStubs(final IElementType parent, final IElementType childType) {
    return false;
  }


  private static class FCTSBackedLighterAST extends LighterAST {
    private final FlyweightCapableTreeStructure<LighterASTNode> myTreeStructure;

    public FCTSBackedLighterAST(final CharTable charTable, final FlyweightCapableTreeStructure<LighterASTNode> treeStructure) {
      super(charTable);
      myTreeStructure = treeStructure;
    }

    @NotNull
    @Override
    public LighterASTNode getRoot() {
      return myTreeStructure.getRoot();
    }

    @Override
    public LighterASTNode getParent(@NotNull final LighterASTNode node) {
      return myTreeStructure.getParent(node);
    }

    @NotNull
    @Override
    public List<LighterASTNode> getChildren(@NotNull final LighterASTNode parent) {
      final Ref<LighterASTNode[]> into = new Ref<LighterASTNode[]>();
      final int numKids = myTreeStructure.getChildren(myTreeStructure.prepareForGetChildren(parent), into);
      return numKids > 0 ? ContainerUtil.newArrayList(into.get(), 0, numKids) : ContainerUtil.<LighterASTNode>emptyList();
    }
  }


  private static class TreeBackedLighterAST extends LighterAST {
    private final FileASTNode myRoot;

    public TreeBackedLighterAST(final FileASTNode root) {
      super(root.getCharTable());
      myRoot = root;
    }

    @NotNull
    @Override
    public LighterASTNode getRoot() {
      //noinspection ConstantConditions
      return wrap(myRoot);
    }

    @Override
    public LighterASTNode getParent(@NotNull final LighterASTNode node) {
      return wrap(((NodeWrapper)node).myNode.getTreeParent());
    }

    @NotNull
    @Override
    public List<LighterASTNode> getChildren(@NotNull final LighterASTNode parent) {
      final ASTNode[] children = ((NodeWrapper)parent).myNode.getChildren(null);
      if (children == null || children.length == 0) {
        return ContainerUtil.emptyList();
      }
      final ArrayList<LighterASTNode> result = new ArrayList<LighterASTNode>(children.length);
      for (final ASTNode child : children) {
        result.add(wrap(child));
      }
      return result;
    }

    @Nullable
    private static LighterASTNode wrap(@Nullable final ASTNode node) {
      if (node == null) return null;
      if (node.getFirstChildNode() == null && node.getTextLength() > 0) {
        return new TokenNodeWrapper(node);
      }
      return new NodeWrapper(node);
    }

    @NotNull
    public ASTNode unwrap(LighterASTNode node) {
      return ((NodeWrapper)node).myNode;
    }

    private static class NodeWrapper implements LighterASTNode {
      protected final ASTNode myNode;

      public NodeWrapper(ASTNode node) {
        myNode = node;
      }

      @Override
      public IElementType getTokenType() {
        return myNode.getElementType();
      }

      @Override
      public int getStartOffset() {
        return myNode.getStartOffset();
      }

      @Override
      public int getEndOffset() {
        return myNode.getStartOffset() + myNode.getTextLength();
      }

      @Override
      public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeWrapper)) return false;
        final NodeWrapper that = (NodeWrapper)o;
        if (myNode != null ? !myNode.equals(that.myNode) : that.myNode != null) return false;
        return true;
      }

      @Override
      public int hashCode() {
        return myNode.hashCode();
      }

      @Override
      public String toString() {
        return "node wrapper[" + myNode + "]";
      }
    }

    private static class TokenNodeWrapper extends NodeWrapper implements LighterASTTokenNode {
      public TokenNodeWrapper(final ASTNode node) {
        super(node);
      }

      @Override
      public CharSequence getText() {
        return myNode.getText();
      }

      @Override
      public String toString() {
        return "token wrapper[" + myNode + "]";
      }
    }
  }
}
