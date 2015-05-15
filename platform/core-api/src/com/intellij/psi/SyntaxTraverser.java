package com.intellij.psi;

import com.intellij.lang.*;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.UnmodifiableIterator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteredTraverser;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * @author gregsh
 */
public abstract class SyntaxTraverser<T> extends FilteredTraverser<T, SyntaxTraverser<T>> implements Iterable<T> {

  @NotNull
  public static SyntaxTraverser<PsiElement> psiTraverser() {
    return new PsiTraverser(null);
  }

  @NotNull
  public static SyntaxTraverser<ASTNode> astTraverser() {
    return new ASTTraverser(null);
  }

  @NotNull
  public static SyntaxTraverser<LighterASTNode> lightTraverser(PsiBuilder builder) {
    FlyweightCapableTreeStructure<LighterASTNode> lightTree = builder.getLightTree();
    Meta<LighterASTNode> meta = FilteredTraverser.<LighterASTNode>emptyMeta().withRoots(Collections.singletonList(lightTree.getRoot()));
    return new LightASTTraverser(meta, builder.getOriginalText(), lightTree);
  }

  protected SyntaxTraverser(@Nullable Meta<T> meta) {
    super(meta);
  }

  @NotNull
  public abstract IElementType nodeType(@NotNull T node);

  @NotNull
  public abstract TextRange nodeRange(@NotNull T node);

  @NotNull
  public abstract CharSequence nodeText(@NotNull T node);

  @Nullable
  public abstract T parent(@NotNull T node);

  public Condition<T> wrapTypeCondition(final Condition<? super IElementType> condition) {
    return new Condition<T>() {
      @Override
      public boolean value(@Nullable T input) {
        return input != null && condition.value(nodeType(input));
      }
    };
  }

  @Nullable
  public T getRawDeepestLast() {
    for (T result = getRoot(), last; result != null; result = last) {
      JBIterable<T> children = children(result);
      if (children.isEmpty()) return result;
      //noinspection AssignmentToForLoopParameter
      last = children.last();
    }
    return null;
  }

  private static class PsiTraverser extends SyntaxTraverser<PsiElement> {

    public PsiTraverser(Meta<PsiElement> meta) {
      super(meta);
    }

    @Override
    protected SyntaxTraverser<PsiElement> newInstance(Meta<PsiElement> meta) {
      return new PsiTraverser(meta);
    }

    @Override
    protected Iterable<PsiElement> childrenImpl(@NotNull final PsiElement node) {
      final Ref<PsiElement> ref = Ref.create(node.getFirstChild());
      if (ref.isNull()) return Collections.emptyList();
      return new JBIterable<PsiElement>() {
        @Override
        public Iterator<PsiElement> iterator() {
          if (ref.isNull()) return ContainerUtil.emptyIterator();
          return new UnmodifiableIterator<PsiElement>(null) {
            @Override
            public boolean hasNext() {
              return !ref.isNull();
            }

            @Override
            public PsiElement next() {
              PsiElement cur = ref.get();
              ref.set(cur.getNextSibling());
              return cur;
            }
          };
        }
      };
    }

    @NotNull
    @Override
    public IElementType nodeType(@NotNull PsiElement node) {
      return node.getNode().getElementType();
    }

    @NotNull
    @Override
    public TextRange nodeRange(@NotNull PsiElement node) {
      return node.getTextRange();
    }

    @NotNull
    @Override
    public CharSequence nodeText(@NotNull PsiElement node) {
      return node.getText();
    }

    @Nullable
    @Override
    public PsiElement parent(@NotNull PsiElement node) {
      return node.getParent();
    }
  }

  private static class ASTTraverser extends SyntaxTraverser<ASTNode> {

    public ASTTraverser(Meta<ASTNode> meta) {
      super(meta);
    }

    @Override
    protected SyntaxTraverser<ASTNode> newInstance(Meta<ASTNode> meta) {
      return new ASTTraverser(meta);
    }

    @Override
    protected Iterable<ASTNode> childrenImpl(@NotNull final ASTNode node) {
      final Ref<ASTNode> ref = Ref.create(node.getFirstChildNode());
      if (ref.isNull()) return Collections.emptyList();
      return new JBIterable<ASTNode>() {
        @Override
        public Iterator<ASTNode> iterator() {
          if (ref.isNull()) return ContainerUtil.emptyIterator();
          return new UnmodifiableIterator<ASTNode>(null) {
            @Override
            public boolean hasNext() {
              return !ref.isNull();
            }

            @Override
            public ASTNode next() {
              ASTNode cur = ref.get();
              ref.set(cur.getTreeNext());
              return cur;
            }
          };
        }
      };
    }

    @NotNull
    @Override
    public IElementType nodeType(@NotNull ASTNode node) {
      return node.getElementType();
    }

    @NotNull
    @Override
    public TextRange nodeRange(@NotNull ASTNode node) {
      return node.getTextRange();
    }

    @NotNull
    @Override
    public CharSequence nodeText(@NotNull ASTNode node) {
      return node.getText();
    }

    @Nullable
    @Override
    public ASTNode parent(@NotNull ASTNode node) {
      return node.getTreeParent();
    }
  }

  private abstract static class FlyweightTraverser<T> extends SyntaxTraverser<T> {
    protected final FlyweightCapableTreeStructure<T> myTree;

    FlyweightTraverser(@NotNull Meta<T> meta,
                       @NotNull FlyweightCapableTreeStructure<T> structure) {
      super(meta);
      myTree = structure;
    }

    @Nullable
    @Override
    public T parent(@NotNull T node) {
      return myTree.getParent(node);
    }

    @Override
    protected Iterable<T> childrenImpl(@NotNull final T node) {
      return new JBIterable<T>() {
        @Override
        public Iterator<T> iterator() {
          Ref<T[]> ref = Ref.create();
          int count = myTree.getChildren(myTree.prepareForGetChildren(node), ref);
          if (count == 0) return ContainerUtil.emptyIterator();
          T[] array = ref.get();
          LinkedList<T> list = ContainerUtil.newLinkedList();
          for (int i = 0; i < count; i++) {
            T t = array[i];
            if (nodeType(t).getLanguage() == Language.ANY) continue; // skip TokenType.* types
            array[i] = null; // do not dispose meaningful TokenNodes
            list.addLast(t);
          }
          myTree.disposeChildren(array, count);
          return list.iterator();
        }
      };
    }

  }

  private static class LightASTTraverser extends FlyweightTraverser<LighterASTNode> {
    private final CharSequence myText;

    public LightASTTraverser(@NotNull Meta<LighterASTNode> meta,
                             @NotNull CharSequence text,
                             @NotNull FlyweightCapableTreeStructure<LighterASTNode> structure) {
      super(meta, structure);
      myText = text;
    }

    @Override
    protected SyntaxTraverser<LighterASTNode> newInstance(Meta<LighterASTNode> meta) {
      return new LightASTTraverser(meta, myText, myTree);
    }

    @NotNull
    @Override
    public IElementType nodeType(@NotNull LighterASTNode node) {
      return node.getTokenType();
    }

    @NotNull
    @Override
    public TextRange nodeRange(@NotNull LighterASTNode node) {
      return TextRange.create(node.getStartOffset(), node.getEndOffset());
    }

    @NotNull
    @Override
    public CharSequence nodeText(@NotNull LighterASTNode node) {
      return myText.subSequence(node.getStartOffset(), node.getEndOffset());
    }

    @Nullable
    @Override
    public LighterASTNode parent(@NotNull LighterASTNode node) {
      return node instanceof LighterASTTokenNode ? null : super.parent(node);
    }
  }
}
