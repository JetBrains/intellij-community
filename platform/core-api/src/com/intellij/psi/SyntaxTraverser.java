package com.intellij.psi;

import com.intellij.lang.*;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Function;
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
  public static SyntaxTraverser<PsiElement> revPsiTraverser() {
    return new RevPsiTraverser(null);
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

  @NotNull
  public SyntaxTraverser<T> expandTypes(@NotNull Condition<? super IElementType> condition) {
    return super.expand(Conditions.compose(NODE_TYPE(), condition));
  }

  @NotNull
  public SyntaxTraverser<T> filterTypes(@NotNull Condition<? super IElementType> condition) {
    return super.filter(Conditions.compose(NODE_TYPE(), condition));
  }

  @NotNull
  public Function<T, IElementType> NODE_TYPE() {
    return new Function<T, IElementType>() {
      @Override
      public IElementType fun(T t) {
        return nodeType(t);
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

  @NotNull
  public JBIterable<T> parents(@Nullable final T element) {
    return new JBIterable<T>() {
      @Override
      public Iterator<T> iterator() {
        return new Iterator<T>() {
          T cur = element;

          @Override
          public boolean hasNext() {
            return cur != null;
          }

          @Override
          public T next() {
            T result = cur;
            cur = parent(cur);
            return result;
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  private abstract static class FirstNextTraverser<T> extends SyntaxTraverser<T> {

    public FirstNextTraverser(Meta<T> meta) {
      super(meta);
    }

    @Nullable
    protected abstract T first(@NotNull T node);

    @Nullable
    protected abstract T next(@NotNull T node);

    @Override
    protected final Iterable<T> childrenImpl(@NotNull final T node) {
      final T first = first(node);
      if (first == null) return JBIterable.empty();
      return new JBIterable<T>() {
        @Override
        public Iterator<T> iterator() {
          return new UnmodifiableIterator<T>(null) {
            T cur = first;

            @Override
            public boolean hasNext() {
              return cur != null;
            }

            @Override
            public T next() {
              T result = cur;
              cur = FirstNextTraverser.this.next(cur);
              return result;
            }
          };
        }
      };
    }
  }

  private static class PsiTraverser extends FirstNextTraverser<PsiElement> {

    public PsiTraverser(Meta<PsiElement> meta) {
      super(meta);
    }

    @Override
    protected SyntaxTraverser<PsiElement> newInstance(Meta<PsiElement> meta) {
      return new PsiTraverser(meta);
    }

    @Nullable
    protected PsiElement first(@NotNull PsiElement node) {
      return node.getFirstChild();
    }

    @Nullable
    protected PsiElement next(@NotNull PsiElement node) {
      return node.getNextSibling();
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
      PsiElement parent = node.getParent();
      return parent instanceof PsiFile ? null : parent;
    }
  }

  private static class RevPsiTraverser extends PsiTraverser {

    public RevPsiTraverser(Meta<PsiElement> meta) {
      super(meta);
    }

    @Override
    protected SyntaxTraverser<PsiElement> newInstance(Meta<PsiElement> meta) {
      return new RevPsiTraverser(meta);
    }

    @Nullable
    @Override
    protected PsiElement first(@NotNull PsiElement node) {
      return node.getLastChild();
    }

    @Nullable
    @Override
    protected PsiElement next(@NotNull PsiElement node) {
      return node.getPrevSibling();
    }
  }

  private static class ASTTraverser extends FirstNextTraverser<ASTNode> {

    public ASTTraverser(Meta<ASTNode> meta) {
      super(meta);
    }

    @Override
    protected SyntaxTraverser<ASTNode> newInstance(Meta<ASTNode> meta) {
      return new ASTTraverser(meta);
    }

    @Nullable
    @Override
    protected ASTNode first(@NotNull ASTNode node) {
      return node.getFirstChildNode();
    }

    @Nullable
    @Override
    protected ASTNode next(@NotNull ASTNode node) {
      return node.getTreeNext();
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
    final FlyweightCapableTreeStructure<T> myTree;

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
