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
package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.psi.*;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.iterators.HierarchyNodeIterator;

/**
 * @author Maxim.Mossienko
 * Date: Mar 23, 2004
 * Time: 6:37:15 PM
 */
public class ExprTypePredicate extends MatchPredicate {
  private final RegExpPredicate delegate;
  private final boolean withinHierarchy;

  public ExprTypePredicate(String type, String baseName, boolean _withinHierarchy, boolean caseSensitiveMatch, boolean target) {
    delegate = new RegExpPredicate(type, caseSensitiveMatch, baseName, false, target);
    withinHierarchy = _withinHierarchy;
  }

  @Override
  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    return match(patternNode, matchedNode, 0, -1, context);
  }

  @Override
  public boolean match(PsiElement node, PsiElement match, int start, int end, MatchContext context) {
    if (match instanceof PsiIdentifier) {
      // since we pickup tokens
      match = match.getParent();
    }
    else if (match instanceof PsiExpressionStatement) {
      match = ((PsiExpressionStatement)match).getExpression();
    }

    if (!(match instanceof PsiExpression)) {
      return false;
    }
    final PsiType type = evalType((PsiExpression)match, context);
    return type != null && doMatchWithTheType(type, context, match);
  }

  protected PsiType evalType(PsiExpression match, MatchContext context) {
    if (match instanceof PsiReferenceExpression) {
      final PsiElement parent = match.getParent();
      if (parent instanceof PsiMethodCallExpression) {
        return ((PsiMethodCallExpression)parent).getType();
      }
    }
    return match.getType();
  }

  private boolean doMatchWithTheType(final PsiType type, MatchContext context, PsiElement matchedNode) {
    if (type instanceof PsiClassType) {
      PsiClass clazz = ((PsiClassType)type).resolve();

      if (clazz!=null) return checkClass(clazz, context);
    }

    if (type!=null) {
      final String presentableText = type.getPresentableText();
      boolean result = delegate.doMatch(presentableText, context, matchedNode);

      if (!result && type instanceof PsiArrayType && ((PsiArrayType)type).getComponentType() instanceof PsiClassType) {
        PsiClass clazz = ((PsiClassType)((PsiArrayType)type).getComponentType()).resolve();

        if (clazz!=null) { // presentable text for array is not qualified!
          result = delegate.doMatch(clazz.getQualifiedName() + "[]", context, matchedNode);
        }
      }
      return result;
    } else {
      return false;
    }
  }

  private boolean checkClass(PsiClass clazz, MatchContext context) {
    if (withinHierarchy) {
      final NodeIterator parents = new HierarchyNodeIterator(clazz, true, true);

      while(parents.hasNext() && !delegate.match(null, parents.current(), context)) {
        parents.advance();
      }

      return parents.hasNext();
    } else {
      return delegate.match(null, clazz, context);
    }
  }
}
