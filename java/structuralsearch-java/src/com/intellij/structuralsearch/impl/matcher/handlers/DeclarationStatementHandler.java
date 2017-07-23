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
package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.dupLocator.iterators.ArrayBackedNodeIterator;
import com.intellij.dupLocator.iterators.CountingNodeIterator;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.iterators.SsrFilteringNodeIterator;

import java.util.ArrayList;
import java.util.List;

/**
 * @author maxim
 * Date: 31.12.2004
 * Time: 12:01:29
 */
public class DeclarationStatementHandler extends MatchingHandler {
  private MatchingHandler myCommentHandler;

  @Override
  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    if (patternNode instanceof PsiComment) {
        return myCommentHandler.match(patternNode, matchedNode, context);
    }

    if (!super.match(patternNode,matchedNode,context)) return false;
    final PsiDeclarationStatement dcl = (PsiDeclarationStatement)patternNode;
    if (matchedNode instanceof PsiDeclarationStatement) {
      return context.getMatcher().matchSequentially(new SsrFilteringNodeIterator(patternNode.getFirstChild()),
                                                    new SsrFilteringNodeIterator(matchedNode.getFirstChild()));
    }
    final PsiElement[] declared = dcl.getDeclaredElements();

    // declaration statement could wrap class or dcl
    if (declared.length > 0 && !(matchedNode.getParent() instanceof PsiDeclarationStatement) /* skip twice matching for child*/) {
      if (!(matchedNode instanceof PsiField)) {
        return context.getMatcher().matchSequentially(
          new ArrayBackedNodeIterator(declared),
          new CountingNodeIterator(declared.length, new SsrFilteringNodeIterator(matchedNode))
        );
      }

      // special handling for multiple fields in single declaration
      final PsiElement sibling = PsiTreeUtil.skipWhitespacesBackward(matchedNode);
      if (PsiUtil.isJavaToken(sibling, JavaTokenType.COMMA)) {
        return false;
      }
      final List<PsiElement> matchNodes = new ArrayList<>();
      matchNodes.add(matchedNode);
      PsiElement node = matchedNode;
      node = PsiTreeUtil.skipWhitespacesForward(node);
      while (PsiUtil.isJavaToken(node, JavaTokenType.COMMA)) {
        node = PsiTreeUtil.skipWhitespacesForward(node);
        if (node instanceof PsiField) {
          matchNodes.add(node);
        }
        node = PsiTreeUtil.skipWhitespacesForward(node);
      }
      boolean result = context.getMatcher().matchSequentially(
        new ArrayBackedNodeIterator(declared),
        new ArrayBackedNodeIterator(matchNodes.toArray(new PsiElement[matchNodes.size()]))
      );

      if (result && declared[0] instanceof PsiVariable) {
        // we may have comments behind to match!

        final PsiElement lastChild = dcl.getLastChild();
        if (lastChild instanceof PsiComment) {
          final PsiElement[] fieldChildren = matchedNode.getChildren();

          result = context.getPattern().getHandler(lastChild).match(
            lastChild,
            fieldChildren[fieldChildren.length-1],
            context
          );
        }
      }
      return result;
    }
    return false;
  }

  @Override
  public boolean shouldAdvanceTheMatchFor(PsiElement patternElement, PsiElement matchedElement) {
    if (patternElement instanceof PsiComment && (matchedElement instanceof PsiField || matchedElement instanceof PsiClass)) {
      return false;
    }

    return super.shouldAdvanceTheMatchFor(patternElement,matchedElement);
  }

  public void setCommentHandler(final MatchingHandler commentHandler) {
    myCommentHandler = commentHandler;
  }
}
