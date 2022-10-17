// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.lang.regexp.RegExpBundle;
import org.intellij.lang.regexp.RegExpLanguageHosts;
import org.intellij.lang.regexp.inspection.ShredManager.ShredInfo;
import org.intellij.lang.regexp.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class UnexpectedAnchorInspection extends LocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new UnexpectedAnchorVisitor(holder);
  }

  private static class UnexpectedAnchorVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;

    UnexpectedAnchorVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitRegExpBoundary(RegExpBoundary boundary) {
      super.visitRegExpBoundary(boundary);
      final RegExpBoundary.Type type = boundary.getType();
      boolean unexpected = switch (type) {
        case BEGIN -> // \A
          hasUnexpectedSibling(boundary, false, false);
        case LINE_START -> // ^
          hasUnexpectedSibling(boundary, false, true);
        case END /* \z */, END_NO_LINE_TERM /* \Z */ ->
          hasUnexpectedSibling(boundary, true, false);
        case LINE_END -> // $
          hasUnexpectedSibling(boundary, true, true);
        default -> false;
      };
      if (unexpected) {
        myHolder.registerProblem(boundary, RegExpBundle.message("inspection.warning.anchor.code.ref.code.in.unexpected.position"));
      }
    }

    private static boolean hasUnexpectedSibling(PsiElement element, boolean next, boolean line) {
      final PsiElement sibling = next
                                 ? PsiTreeUtil.skipSiblingsForward(element, PsiComment.class, PsiWhiteSpace.class, RegExpSetOptions.class)
                                 : PsiTreeUtil.skipSiblingsBackward(element, PsiComment.class, PsiWhiteSpace.class, RegExpSetOptions.class);
      if (sibling == null) {
        return false;
      }
      return !line || isUnexpectedSibling(element, next, sibling);
    }

    private static boolean isUnexpectedSibling(PsiElement element, boolean next, PsiElement sibling) {
      if (sibling instanceof RegExpChar) {
        final int value = ((RegExpChar)sibling).getValue();
        if (value == ' ') {
          ShredManager shredManager = new ShredManager(element);
          ShredInfo shredInfo = shredManager.getShredInfo(element.getParent().getText());
          if (shredInfo == null || shredInfo.getHost() == null) return true;
          if (RegExpLanguageHosts.getInstance().belongsToConditionalExpression(element, shredInfo.getHost())) {
            return shredManager.containsCloseRealWhiteSpace(shredInfo, next);
          }
        }
        return value != '\n' && value != '\r';
      }
      else if (sibling instanceof RegExpSimpleClass) {
        final RegExpSimpleClass.Kind kind = ((RegExpSimpleClass)sibling).getKind();
        return switch (kind) {
          case ANY, NON_DIGIT, NON_WORD, SPACE, NON_HORIZONTAL_SPACE, NON_VERTICAL_SPACE, NON_XML_NAME_START, NON_XML_NAME_PART,
            UNICODE_LINEBREAK -> false;
          default -> true;
        };
      }
      else if (sibling instanceof  RegExpClosure) {
        final RegExpClosure closure = (RegExpClosure)sibling;
        return isUnexpectedSibling(element, next, closure.getAtom());
      }
      else if (sibling instanceof RegExpGroup) {
        final RegExpGroup group = (RegExpGroup)sibling;
        return isUnexpectedSibling(element, next, group.getPattern());
      }
      else if (sibling instanceof RegExpPattern) {
        final RegExpPattern pattern = (RegExpPattern)sibling;
        for (RegExpBranch branch : pattern.getBranches()) {
          if (isUnexpectedSibling(element, next, branch)) {
            return true;
          }
        }
        return false;
      }
      else if (sibling instanceof RegExpBranch) {
        final RegExpBranch branch = (RegExpBranch)sibling;
        for (RegExpAtom atom : branch.getAtoms()) {
          if (isUnexpectedSibling(element, next, atom)) {
            return true;
          }
        }
        return false;
      }
      return sibling instanceof RegExpBoundary;
    }
  }
}
