// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.LighterASTTokenNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.util.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteredTraverserBase;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedList;

import static com.intellij.openapi.util.Conditions.compose;

/**
 * @author gregsh
 */
public class SyntaxTraverser<T> extends FilteredTraverserBase<T, SyntaxTraverser<T>> implements UserDataHolder {

  @NotNull
  public static ApiEx<PsiElement> psiApi() {
    return PsiApi.INSTANCE;
  }

  @NotNull
  public static ApiEx<PsiElement> psiApiReversed() {
    return PsiApi.INSTANCE_REV;
  }

  @NotNull
  public static ApiEx<ASTNode> astApi() {
    return ASTApi.INSTANCE;
  }

  @NotNull
  public static Api<LighterASTNode> lightApi(@NotNull PsiBuilder builder) {
    return new LighterASTApi(builder);
  }

  @NotNull
  public static <T> SyntaxTraverser<T> syntaxTraverser(@NotNull Api<T> api) {
    return new SyntaxTraverser<>(api, null);
  }

  @NotNull
  public static SyntaxTraverser<PsiElement> psiTraverser() {
    return new SyntaxTraverser<>(psiApi(), null);
  }

  @NotNull
  public static SyntaxTraverser<PsiElement> psiTraverser(@Nullable PsiElement root) {
    return psiTraverser().withRoot(root);
  }

  @NotNull
  public static SyntaxTraverser<PsiElement> revPsiTraverser() {
    return new SyntaxTraverser<>(psiApiReversed(), null);
  }

  @NotNull
  public static SyntaxTraverser<ASTNode> astTraverser() {
    return new SyntaxTraverser<>(astApi(), null);
  }

  @NotNull
  public static SyntaxTraverser<ASTNode> astTraverser(@Nullable ASTNode root) {
    return astTraverser().withRoot(root);
  }

  @NotNull
  public static SyntaxTraverser<LighterASTNode> lightTraverser(@NotNull PsiBuilder builder) {
    LighterASTApi api = new LighterASTApi(builder);
    return new SyntaxTraverser<>(api, Meta.<LighterASTNode>empty().withRoots(JBIterable.of(api.getStructure().getRoot())));
  }

  public final Api<T> api;

  protected SyntaxTraverser(@NotNull Api<T> api, @Nullable Meta<T> meta) {
    super(meta, api);
    this.api = api;
  }

  @NotNull
  @Override
  protected SyntaxTraverser<T> newInstance(Meta<T> meta) {
    return new SyntaxTraverser<>(api, meta);
  }

  @Override
  protected boolean isAlwaysLeaf(@NotNull T node) {
    return super.isAlwaysLeaf(node) && !(api.typeOf(node) instanceof IFileElementType);
  }

  @Nullable
  @Override
  public <K> K getUserData(@NotNull Key<K> key) {
    return getUserDataHolder().getUserData(key);
  }

  @Override
  public <K> void putUserData(@NotNull Key<K> key, @Nullable K value) {
    getUserDataHolder().putUserData(key, value);
  }

  private UserDataHolder getUserDataHolder() {
    return api instanceof LighterASTApi ? ((LighterASTApi)api).userDataHolder : (UserDataHolder)api.parents(getRoot()).last();
  }

  @NotNull
  public SyntaxTraverser<T> expandTypes(@NotNull Condition<? super IElementType> c) {
    return super.expand(compose(api.TO_TYPE, c));
  }

  @NotNull
  public SyntaxTraverser<T> filterTypes(@NotNull Condition<? super IElementType> c) {
    return super.filter(compose(api.TO_TYPE, c));
  }

  @NotNull
  public SyntaxTraverser<T> forceDisregardTypes(@NotNull Condition<? super IElementType> c) {
    return super.forceDisregard(compose(api.TO_TYPE, c));
  }

  @Nullable
  public T getRawDeepestLast() {
    for (T result = JBIterable.from(getRoots()).last(), last; result != null; result = last) {
      JBIterable<T> children = children(result);
      if (children.isEmpty()) return result;
      //noinspection AssignmentToForLoopParameter
      last = children.last();
    }
    return null;
  }

  @NotNull
  public final SyntaxTraverser<T> onRange(@NotNull final TextRange range) {
    return onRange(e -> api.rangeOf(e).intersects(range));
  }

  public abstract static class Api<T> implements Function<T, Iterable<? extends T>> {
    @NotNull
    public abstract IElementType typeOf(@NotNull T node);

    @NotNull
    public abstract TextRange rangeOf(@NotNull T node);

    @NotNull
    public abstract CharSequence textOf(@NotNull T node);

    @Nullable
    public abstract T parent(@NotNull T node);

    @NotNull
    public abstract JBIterable<? extends T> children(@NotNull T node);

    @Override
    public JBIterable<? extends T> fun(T t) {
      return children(t);
    }

    @NotNull
    public JBIterable<T> parents(@Nullable final T element) {
      return JBIterable.generate(element, t -> parent(t));
    }

    public final Function<T, IElementType> TO_TYPE = new Function<T, IElementType>() {
      @Override
      public IElementType fun(T t) {
        return typeOf(t);
      }

      @Override
      public String toString() {
        return "TO_TYPE";
      }
    };

    public final Function<T, CharSequence> TO_TEXT = new Function<T, CharSequence>() {
      @Override
      public CharSequence fun(T t) {
        return textOf(t);
      }

      @Override
      public String toString() {
        return "TO_TEXT";
      }
    };

    public final Function<T, TextRange> TO_RANGE = new Function<T, TextRange>() {
      @Override
      public TextRange fun(T t) {
        return rangeOf(t);
      }

      @Override
      public String toString() {
        return "TO_RANGE";
      }
    };
  }

  public abstract static class ApiEx<T> extends Api<T> {

    @Nullable
    public abstract T first(@NotNull T node);

    @Nullable
    public abstract T last(@NotNull T node);

    @Nullable
    public abstract T next(@NotNull T node);

    @Nullable
    public abstract T previous(@NotNull T node);

    @NotNull
    @Override
    public JBIterable<? extends T> children(@NotNull T node) {
      T first = first(node);
      if (first == null) return JBIterable.empty();
      return siblings(first);
    }

    @NotNull
    public JBIterable<? extends T> siblings(@NotNull T node) {
      return JBIterable.generate(node, TO_NEXT);
    }

    private final Function<T, T> TO_NEXT = new Function<T, T>() {
      @Override
      public T fun(T t) {
        return next(t);
      }

      @Override
      public String toString() {
        return "TO_NEXT";
      }
    };
  }

  private static class PsiApi extends ApiEx<PsiElement> {

    static final ApiEx<PsiElement> INSTANCE = new PsiApi();
    static final ApiEx<PsiElement> INSTANCE_REV = new PsiApi() {
      @Nullable
      @Override
      public PsiElement previous(@NotNull PsiElement node) {
        return super.next(node);
      }

      @Nullable
      @Override
      public PsiElement next(@NotNull PsiElement node) {
        return super.previous(node);
      }

      @Nullable
      @Override
      public PsiElement last(@NotNull PsiElement node) {
        return super.first(node);
      }

      @Nullable
      @Override
      public PsiElement first(@NotNull PsiElement node) {
        return super.last(node);
      }
    };

    @Nullable
    @Override
    public PsiElement first(@NotNull PsiElement node) {
      return node.getFirstChild();
    }

    @Nullable
    @Override
    public PsiElement last(@NotNull PsiElement node) {
      return node.getLastChild();
    }

    @Nullable
    @Override
    public PsiElement next(@NotNull PsiElement node) {
      return node.getNextSibling();
    }

    @Nullable
    @Override
    public PsiElement previous(@NotNull PsiElement node) {
      return node.getPrevSibling();
    }

    @NotNull
    @Override
    public IElementType typeOf(@NotNull PsiElement node) {
      IElementType type = PsiUtilCore.getElementType(node);
      return type != null ? type : IElementType.find((short)0);
    }

    @NotNull
    @Override
    public TextRange rangeOf(@NotNull PsiElement node) {
      return node.getTextRange();
    }

    @NotNull
    @Override
    public CharSequence textOf(@NotNull PsiElement node) {
      return node.getText();
    }

    @Nullable
    @Override
    public PsiElement parent(@NotNull PsiElement node) {
      return node instanceof PsiFile ? null : node.getParent();
    }
  }

  private static class ASTApi extends ApiEx<ASTNode> {

    static final ASTApi INSTANCE = new ASTApi();

    @Nullable
    @Override
    public ASTNode first(@NotNull ASTNode node) {
      return node.getFirstChildNode();
    }

    @Nullable
    @Override
    public ASTNode last(@NotNull ASTNode node) {
      return node.getLastChildNode();
    }

    @Nullable
    @Override
    public ASTNode next(@NotNull ASTNode node) {
      return node.getTreeNext();
    }

    @Nullable
    @Override
    public ASTNode previous(@NotNull ASTNode node) {
      return node.getTreePrev();
    }

    @NotNull
    @Override
    public IElementType typeOf(@NotNull ASTNode node) {
      return node.getElementType();
    }

    @NotNull
    @Override
    public TextRange rangeOf(@NotNull ASTNode node) {
      return node.getTextRange();
    }

    @NotNull
    @Override
    public CharSequence textOf(@NotNull ASTNode node) {
      return node.getText();
    }

    @Nullable
    @Override
    public ASTNode parent(@NotNull ASTNode node) {
      return node.getTreeParent();
    }
  }

  private abstract static class FlyweightApi<T> extends Api<T> {

    @NotNull
    abstract FlyweightCapableTreeStructure<T> getStructure();

    @Nullable
    @Override
    public T parent(@NotNull T node) {
      return getStructure().getParent(node);
    }

    @NotNull
    @Override
    public JBIterable<? extends T> children(@NotNull final T node) {
      return new JBIterable<T>() {
        @Override
        public Iterator<T> iterator() {
          FlyweightCapableTreeStructure<T> structure = getStructure();
          Ref<T[]> ref = Ref.create();
          int count = structure.getChildren(node, ref);
          if (count == 0) return ContainerUtil.emptyIterator();
          T[] array = ref.get();
          LinkedList<T> list = ContainerUtil.newLinkedList();
          for (int i = 0; i < count; i++) {
            T child = array[i];
            IElementType childType = typeOf(child);
            // tokens and errors getParent() == null
            if (childType == TokenType.WHITE_SPACE || childType == TokenType.BAD_CHARACTER) {
              continue;
            }
            array[i] = null; // do not dispose meaningful TokenNodes
            list.addLast(child);
          }
          structure.disposeChildren(array, count);
          return list.iterator();
        }
      };
    }
  }

  private static class LighterASTApi extends FlyweightApi<LighterASTNode> {
    private final PsiBuilder builder;
    private final UserDataHolder userDataHolder = new UserDataHolderBase();
    private final ThreadLocalCachedValue<FlyweightCapableTreeStructure<LighterASTNode>> structure =
      new ThreadLocalCachedValue<FlyweightCapableTreeStructure<LighterASTNode>>() {
        @Override
        protected FlyweightCapableTreeStructure<LighterASTNode> create() {
          return builder.getLightTree();
        }
      };

    public LighterASTApi(final PsiBuilder builder) {
      this.builder = builder;
    }

    @NotNull
    @Override
    FlyweightCapableTreeStructure<LighterASTNode> getStructure() {
      return structure.getValue();
    }

    @NotNull
    @Override
    public IElementType typeOf(@NotNull LighterASTNode node) {
      return node.getTokenType();
    }

    @NotNull
    @Override
    public TextRange rangeOf(@NotNull LighterASTNode node) {
      return TextRange.create(node.getStartOffset(), node.getEndOffset());
    }

    @NotNull
    @Override
    public CharSequence textOf(@NotNull LighterASTNode node) {
      return rangeOf(node).subSequence(builder.getOriginalText());
    }

    @Nullable
    @Override
    public LighterASTNode parent(@NotNull LighterASTNode node) {
      return node instanceof LighterASTTokenNode ? null : super.parent(node);
    }
  }
}
