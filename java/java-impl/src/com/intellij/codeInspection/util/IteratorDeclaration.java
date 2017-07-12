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
package com.intellij.codeInspection.util;

import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the iterator which traverses the iterable within the loop
 *
 * @author Tagir Valeev
 */
public class IteratorDeclaration {
  private final @NotNull PsiLocalVariable myIterator;
  private final @Nullable PsiExpression myIterable;
  private final boolean myCollection;

  private IteratorDeclaration(@NotNull PsiLocalVariable iterator, @Nullable PsiExpression iterable, boolean collection) {
    myIterator = iterator;
    myIterable = iterable;
    myCollection = collection;
  }

  @NotNull
  public PsiLocalVariable getIterator() {
    return myIterator;
  }

  @Nullable
  public PsiExpression getIterable() {
    return myIterable;
  }

  public boolean isCollection() {
    return myCollection;
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
    return StreamEx.of(DefUseUtil.getRefs(block, myIterator, myIterator.getInitializer()))
      .filter(e -> PsiTreeUtil.isAncestor(parent, e, false))
      .collect(MoreCollectors.onlyOne()).orElse(null);
  }

  public boolean isIteratorMethodCall(PsiElement candidate, String method) {
    if (!(candidate instanceof PsiMethodCallExpression)) return false;
    PsiMethodCallExpression call = (PsiMethodCallExpression)candidate;
    if (call.getArgumentList().getExpressions().length != 0) return false;
    PsiReferenceExpression expression = call.getMethodExpression();
    return method.equals(expression.getReferenceName()) && ExpressionUtils.isReferenceTo(expression.getQualifierExpression(), myIterator);
  }

  public PsiVariable getNextElementVariable(PsiStatement statement) {
    if (!(statement instanceof PsiDeclarationStatement)) return null;
    PsiDeclarationStatement declaration = (PsiDeclarationStatement)statement;
    if (declaration.getDeclaredElements().length != 1) return null;
    PsiElement element = declaration.getDeclaredElements()[0];
    if (!(element instanceof PsiLocalVariable)) return null;
    PsiLocalVariable var = (PsiLocalVariable)element;
    if (!isIteratorMethodCall(var.getInitializer(), "next")) return null;
    return var;
  }

  @Contract("null -> null")
  private static IteratorDeclaration extract(PsiStatement statement) {
    if (!(statement instanceof PsiDeclarationStatement)) return null;
    PsiDeclarationStatement declaration = (PsiDeclarationStatement)statement;
    if (declaration.getDeclaredElements().length != 1) return null;
    PsiElement element = declaration.getDeclaredElements()[0];
    if (!(element instanceof PsiLocalVariable)) return null;
    PsiLocalVariable variable = (PsiLocalVariable)element;
    PsiExpression initializer = variable.getInitializer();
    if (!(initializer instanceof PsiMethodCallExpression)) return null;
    PsiMethodCallExpression call = (PsiMethodCallExpression)initializer;
    if (call.getArgumentList().getExpressions().length != 0) return null;
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
    if (!ReferencesSearch.search(declaration.myIterator, declaration.myIterator.getUseScope()).forEach(ref -> {
      return PsiTreeUtil.isAncestor(statement, ref.getElement(), true);
    })) {
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
