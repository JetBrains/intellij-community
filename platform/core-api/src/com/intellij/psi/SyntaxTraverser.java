// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.containers.FilteredTraverserBase;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;

import static com.intellij.openapi.util.Conditions.compose;
import static com.intellij.openapi.util.Conditions.instanceOf;

/**
 * @author gregsh
 */
public class SyntaxTraverser<T> extends FilteredTraverserBase<T, SyntaxTraverser<T>> implements UserDataHolder {

  public static @NotNull ApiEx<PsiElement> psiApi() {
    return PsiApi.INSTANCE;
  }

  public static @NotNull ApiEx<PsiElement> psiApiReversed() {
    return PsiApi.INSTANCE_REV;
  }

  public static @NotNull ApiEx<ASTNode> astApi() {
    return ASTApi.INSTANCE;
  }

  public static @NotNull Api<LighterASTNode> lightApi(@NotNull PsiBuilder builder) {
    return new LighterASTApi(builder);
  }

  public static @NotNull <T> SyntaxTraverser<T> syntaxTraverser(@NotNull Api<T> api) {
    return new SyntaxTraverser<>(api, null);
  }

  public static @NotNull SyntaxTraverser<PsiElement> psiTraverser() {
    return new SyntaxTraverser<>(psiApi(), null);
  }

  public static @NotNull SyntaxTraverser<PsiElement> psiTraverser(@Nullable PsiElement root) {
    return psiTraverser().withRoot(root);
  }

  public static @NotNull SyntaxTraverser<PsiElement> revPsiTraverser() {
    return new SyntaxTraverser<>(psiApiReversed(), null);
  }

  public static @NotNull SyntaxTraverser<ASTNode> astTraverser() {
    return new SyntaxTraverser<>(astApi(), null);
  }

  public static @NotNull SyntaxTraverser<ASTNode> astTraverser(@Nullable ASTNode root) {
    return astTraverser().withRoot(root);
  }

  public static @NotNull SyntaxTraverser<LighterASTNode> lightTraverser(@NotNull PsiBuilder builder) {
    LighterASTApi api = new LighterASTApi(builder);
    Meta<LighterASTNode> meta = Meta.create(api)
      .forceExpand(compose(api::typeOf, instanceOf(IFileElementType.class)))
      .withRoots(JBIterable.of(api.getStructure().getRoot()));
    return new SyntaxTraverser<>(api, meta);
  }

  public final Api<T> api;

  protected SyntaxTraverser(@NotNull Api<T> api, @Nullable Meta<T> meta) {
    super(meta == null ? Meta.create(api).forceExpand(compose(api::typeOf, instanceOf(IFileElementType.class))) : meta);
    this.api = api;
  }

  @Override
  protected @NotNull SyntaxTraverser<T> newInstance(@NotNull Meta<T> meta) {
    return new SyntaxTraverser<>(api, meta);
  }

  public final @NotNull <S> SyntaxTraverser<S> map(@NotNull Function<? super T, ? extends S> function,
                                                   @NotNull Function<? super S, ? extends T> reverse) {
    return super.mapImpl(function, reverse);
  }

  public final @NotNull <S> SyntaxTraverser<S> map(@NotNull Function<? super T, ? extends S> function) {
    return super.mapImpl(function);
  }

  @Override
  public @Nullable <K> K getUserData(@NotNull Key<K> key) {
    return getUserDataHolder().getUserData(key);
  }

  @Override
  public <K> void putUserData(@NotNull Key<K> key, @Nullable K value) {
    getUserDataHolder().putUserData(key, value);
  }

  private UserDataHolder getUserDataHolder() {
    return api instanceof LighterASTApi ? ((LighterASTApi)api).userDataHolder :
           (UserDataHolder)api.parents(getRoot()).last();
  }

  public @NotNull SyntaxTraverser<T> expandTypes(@NotNull Condition<? super IElementType> c) {
    return super.expand(compose(api.TO_TYPE, c));
  }

  public @NotNull SyntaxTraverser<T> filterTypes(@NotNull Condition<? super IElementType> c) {
    return super.filter(compose(api.TO_TYPE, c));
  }

  public @NotNull SyntaxTraverser<T> forceDisregardTypes(@NotNull Condition<? super IElementType> c) {
    return super.forceDisregard(compose(api.TO_TYPE, c));
  }

  public @Nullable T getRawDeepestLast() {
    for (T result = JBIterable.from(getRoots()).last(), last; result != null; result = last) {
      JBIterable<T> children = children(result);
      if (children.isEmpty()) return result;
      //noinspection AssignmentToForLoopParameter
      last = children.last();
    }
    return null;
  }

  public final @NotNull SyntaxTraverser<T> onRange(final @NotNull TextRange range) {
    return onRange(e -> api.rangeOf(e).intersects(range));
  }

  public abstract static class Api<T> implements Function<T, Iterable<? extends T>> {
    public abstract @NotNull IElementType typeOf(@NotNull T node);

    public abstract @NotNull TextRange rangeOf(@NotNull T node);

    public abstract @NotNull CharSequence textOf(@NotNull T node);

    public abstract @Nullable T parent(@NotNull T node);

    public abstract @NotNull JBIterable<? extends T> children(@NotNull T node);

    @Override
    public JBIterable<? extends T> fun(T t) {
      return children(t);
    }

    public @NotNull JBIterable<T> parents(final @Nullable T element) {
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

    public abstract @Nullable T first(@NotNull T node);

    public abstract @Nullable T last(@NotNull T node);

    public abstract @Nullable T next(@NotNull T node);

    public abstract @Nullable T previous(@NotNull T node);

    @Override
    public @NotNull JBIterable<? extends T> children(@NotNull T node) {
      T first = first(node);
      if (first == null) return JBIterable.empty();
      return siblings(first);
    }

    public @NotNull JBIterable<? extends T> siblings(@NotNull T node) {
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
      @Override
      public @Nullable PsiElement previous(@NotNull PsiElement node) {
        return super.next(node);
      }

      @Override
      public @Nullable PsiElement next(@NotNull PsiElement node) {
        return super.previous(node);
      }

      @Override
      public @Nullable PsiElement last(@NotNull PsiElement node) {
        return super.first(node);
      }

      @Override
      public @Nullable PsiElement first(@NotNull PsiElement node) {
        return super.last(node);
      }
    };

    @Override
    public @Nullable PsiElement first(@NotNull PsiElement node) {
      return node.getFirstChild();
    }

    @Override
    public @Nullable PsiElement last(@NotNull PsiElement node) {
      return node.getLastChild();
    }

    @Override
    public @Nullable PsiElement next(@NotNull PsiElement node) {
      return node.getNextSibling();
    }

    @Override
    public @Nullable PsiElement previous(@NotNull PsiElement node) {
      return node.getPrevSibling();
    }

    @Override
    public @NotNull IElementType typeOf(@NotNull PsiElement node) {
      IElementType type = PsiUtilCore.getElementType(node);
      return type != null ? type : IElementType.find((short)0);
    }

    @Override
    public @NotNull TextRange rangeOf(@NotNull PsiElement node) {
      return node.getTextRange();
    }

    @Override
    public @NotNull CharSequence textOf(@NotNull PsiElement node) {
      return node.getText();
    }

    @Override
    public @Nullable PsiElement parent(@NotNull PsiElement node) {
      return node instanceof PsiFile ? null : node.getParent();
    }
  }

  private static class ASTApi extends ApiEx<ASTNode> {

    static final ASTApi INSTANCE = new ASTApi();

    @Override
    public @Nullable ASTNode first(@NotNull ASTNode node) {
      return node.getFirstChildNode();
    }

    @Override
    public @Nullable ASTNode last(@NotNull ASTNode node) {
      return node.getLastChildNode();
    }

    @Override
    public @Nullable ASTNode next(@NotNull ASTNode node) {
      return node.getTreeNext();
    }

    @Override
    public @Nullable ASTNode previous(@NotNull ASTNode node) {
      return node.getTreePrev();
    }

    @Override
    public @NotNull IElementType typeOf(@NotNull ASTNode node) {
      return node.getElementType();
    }

    @Override
    public @NotNull TextRange rangeOf(@NotNull ASTNode node) {
      return node.getTextRange();
    }

    @Override
    public @NotNull CharSequence textOf(@NotNull ASTNode node) {
      return node.getText();
    }

    @Override
    public @Nullable ASTNode parent(@NotNull ASTNode node) {
      return node.getTreeParent();
    }
  }

  private abstract static class FlyweightApi<T> extends Api<T> {

    abstract @NotNull FlyweightCapableTreeStructure<T> getStructure();

    @Override
    public @Nullable T parent(@NotNull T node) {
      return getStructure().getParent(node);
    }

    @Override
    public @NotNull JBIterable<? extends T> children(final @NotNull T node) {
      return new JBIterable<T>() {
        @Override
        public Iterator<T> iterator() {
          FlyweightCapableTreeStructure<T> structure = getStructure();
          Ref<T[]> ref = Ref.create();
          int count = structure.getChildren(node, ref);
          if (count == 0) return Collections.emptyIterator();
          T[] array = ref.get();
          LinkedList<T> list = new LinkedList<>();
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
        protected @NotNull FlyweightCapableTreeStructure<LighterASTNode> create() {
          return builder.getLightTree();
        }
      };

    LighterASTApi(final PsiBuilder builder) {
      this.builder = builder;
    }

    @NotNull
    @Override
    FlyweightCapableTreeStructure<LighterASTNode> getStructure() {
      return structure.getValue();
    }

    @Override
    public @NotNull IElementType typeOf(@NotNull LighterASTNode node) {
      return node.getTokenType();
    }

    @Override
    public @NotNull TextRange rangeOf(@NotNull LighterASTNode node) {
      return TextRange.create(node.getStartOffset(), node.getEndOffset());
    }

    @Override
    public @NotNull CharSequence textOf(@NotNull LighterASTNode node) {
      return rangeOf(node).subSequence(builder.getOriginalText());
    }

    @Override
    public @Nullable LighterASTNode parent(@NotNull LighterASTNode node) {
      return node instanceof LighterASTTokenNode ? null : super.parent(node);
    }
  }
}
