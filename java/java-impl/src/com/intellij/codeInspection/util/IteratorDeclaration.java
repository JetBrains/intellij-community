// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.util;

import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents the iterator which traverses the iterable within the loop
 *
 * @author Tagir Valeev
 */
public final class IteratorDeclaration extends IterableTraversal {
  private final @NotNull PsiLocalVariable myIterator;

  private IteratorDeclaration(@NotNull PsiLocalVariable iterator, @Nullable PsiExpression iterable, boolean collection) {
    super(iterable, collection);
    myIterator = iterator;
  }

  @NotNull
  public PsiLocalVariable getIterator() {
    return myIterator;
  }

  public boolean isHasNextCall(PsiExpression condition) {
    return isIteratorMethodCall(condition, "hasNext");
  }

  @Nullable
  public PsiElement findOnlyIteratorRef(PsiExpression parent) {
    PsiElement element = PsiUtil.getVariableCodeBlock(myIterator, null);
    PsiCodeBlock block =
      element instanceof PsiCodeBlock ? (PsiCodeBlock)element : PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class);
    if (block == null) return null;
    return StreamEx.of(DefUseUtil.getRefs(block, myIterator, Objects.requireNonNull(myIterator.getInitializer())))
      .filter(e -> PsiTreeUtil.isAncestor(parent, e, false))
      .collect(MoreCollectors.onlyOne()).orElse(null);
  }

  public boolean isIteratorMethodCall(PsiElement candidate, String method) {
    while (candidate instanceof PsiParenthesizedExpression) {
      candidate = ((PsiParenthesizedExpression)candidate).getExpression();
    }
    if (!(candidate instanceof PsiMethodCallExpression call)) return false;
    if (!call.getArgumentList().isEmpty()) return false;
    PsiReferenceExpression expression = call.getMethodExpression();
    return method.equals(expression.getReferenceName()) && ExpressionUtils.isReferenceTo(expression.getQualifierExpression(), myIterator);
  }

  @Override
  public boolean isRemoveCall(PsiExpression candidate) {
    return isIteratorMethodCall(candidate, "remove");
  }

  public PsiVariable getNextElementVariable(PsiStatement statement) {
    PsiLocalVariable var = getDeclaredVariable(statement);
    if (var == null || !isIteratorMethodCall(var.getInitializer(), "next")) return null;
    return var;
  }

  @Nullable
  public static PsiLocalVariable getDeclaredVariable(PsiStatement statement) {
    if (!(statement instanceof PsiDeclarationStatement declaration)) return null;
    PsiElement[] elements = declaration.getDeclaredElements();
    if (elements.length != 1) return null;
    return ObjectUtils.tryCast(elements[0], PsiLocalVariable.class);
  }

  @Contract("null -> null")
  private static IteratorDeclaration extract(PsiStatement statement) {
    PsiLocalVariable variable = getDeclaredVariable(statement);
    if (variable == null) return null;
    PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(variable.getInitializer());
    if (!(initializer instanceof PsiMethodCallExpression call)) return null;
    if (!call.getArgumentList().isEmpty()) return null;
    PsiReferenceExpression methodExpression = call.getMethodExpression();
    boolean listIterator = "listIterator".equals(methodExpression.getReferenceName());
    if (!"iterator".equals(methodExpression.getReferenceName()) && !listIterator) return null;
    PsiMethod method = call.resolveMethod();
    if (method == null) return null;
    boolean isCollection;
    if (listIterator) {
      if (!InheritanceUtil.isInheritor(method.getContainingClass(), CommonClassNames.JAVA_UTIL_LIST)) return null;
      isCollection = true;
    } else {
      if (!InheritanceUtil.isInheritor(method.getContainingClass(), CommonClassNames.JAVA_LANG_ITERABLE)) return null;
      isCollection = InheritanceUtil.isInheritor(method.getContainingClass(), CommonClassNames.JAVA_UTIL_COLLECTION);
    }
    if (!InheritanceUtil.isInheritor(variable.getType(), CommonClassNames.JAVA_UTIL_ITERATOR)) return null;
    return new IteratorDeclaration(variable, methodExpression.getQualifierExpression(), isCollection);
  }

  @Nullable
  private static IteratorDeclaration fromForLoop(PsiForStatement statement) {
    if (statement.getUpdate() != null) return null;
    PsiStatement initialization = statement.getInitialization();
    IteratorDeclaration declaration = extract(initialization);
    if (declaration == null || !declaration.isHasNextCall(statement.getCondition())) return null;
    return declaration;
  }

  @Nullable
  private static IteratorDeclaration fromWhileLoop(PsiWhileStatement statement) {
    PsiElement previous = PsiTreeUtil.skipWhitespacesAndCommentsBackward(statement);
    if (!(previous instanceof PsiDeclarationStatement)) return null;
    IteratorDeclaration declaration = extract((PsiStatement)previous);
    if (declaration == null || !declaration.isHasNextCall(statement.getCondition())) return null;
    if (ReferencesSearch.search(declaration.myIterator, declaration.myIterator.getUseScope())
      .anyMatch(ref -> !PsiTreeUtil.isAncestor(statement, ref.getElement(), true))) {
      return null;
    }
    return declaration;
  }

  /**
   * Creates {@code IteratorDeclaration} if the loop follows one of these patterns:
   *
   * <pre>{@code
   * Iterator<T> it = iterable.iterator();
   * while(it.hasNext()) {
   *   ...
   * }
   * // And iterator is not reused after the loop
   * }</pre>
   *
   *  or
   *
   * <pre>{@code
   * for(Iterator<T> it = iterable.iterator();it.hasNext();) {
   *   ...
   * }
   * }</pre>
   *
   * @param statement loop to create the {@code IteratorDeclaration} from
   * @return created IteratorDeclaration or null if the loop pattern is not recognized.
   */
  @Contract("null -> null")
  public static IteratorDeclaration fromLoop(PsiLoopStatement statement) {
    if(statement instanceof PsiWhileStatement) {
      return fromWhileLoop((PsiWhileStatement)statement);
    }
    if(statement instanceof PsiForStatement) {
      return fromForLoop((PsiForStatement)statement);
    }
    return null;
  }
}
