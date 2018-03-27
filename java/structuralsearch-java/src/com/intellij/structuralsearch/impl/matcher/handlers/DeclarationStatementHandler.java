// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
 */
public class DeclarationStatementHandler extends MatchingHandler {
  private MatchingHandler myCommentHandler;

  @Override
  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    if (patternNode instanceof PsiComment) {
        return myCommentHandler.match(patternNode, matchedNode, context);
    }
    if (!super.match(patternNode, matchedNode,context)) {
      return false;
    }

    final PsiDeclarationStatement dcl = (PsiDeclarationStatement)patternNode;
    if (matchedNode instanceof PsiDeclarationStatement) {
      return context.getMatcher().matchSequentially(new SsrFilteringNodeIterator(patternNode.getFirstChild()),
                                                    new SsrFilteringNodeIterator(matchedNode.getFirstChild()));
    }
    final PsiElement[] declared = dcl.getDeclaredElements();

    // declaration statement could wrap class or dcl
    if (declared.length > 0 && (!context.shouldRecursivelyMatch() || !(matchedNode.getParent() instanceof PsiDeclarationStatement)) /* skip twice matching for child*/) {
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
