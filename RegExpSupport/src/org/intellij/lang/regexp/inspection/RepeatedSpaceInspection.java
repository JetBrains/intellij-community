// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiWhiteSpace;
import org.intellij.lang.regexp.RegExpBundle;
import org.intellij.lang.regexp.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class RepeatedSpaceInspection extends LocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new RepeatedSpaceVisitor(holder);
  }

  private static class RepeatedSpaceVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;

    RepeatedSpaceVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitRegExpChar(RegExpChar aChar) {
      if (!isSpace(aChar) || isSpace(aChar.getPrevSibling()) || isInEscapeSequence(aChar)) {
        return;
      }
      final PsiElement parent = aChar.getParent();
      if (parent instanceof RegExpClass || parent instanceof RegExpCharRange) {
        return;
      }
      int count = 1;
      int length = aChar.getTextLength();
      PsiElement next = aChar.getNextSibling();
      while (isSpace(next)) {
        count++;
        length += next.getTextLength();
        next = next.getNextSibling();
      }
      if (count > 1) {
        final String message = RegExpBundle.message("inspection.warning.consecutive.spaces.in.regexp", count);
        final int offset = aChar.getStartOffsetInParent();
        myHolder.registerProblem(parent, new TextRange(offset, offset + length), message, new RepeatedSpaceFix(count));
      }
    }

    private static boolean isInEscapeSequence(RegExpChar aChar) {
      PsiElement prev = aChar.getPrevSibling();
      while (prev instanceof RegExpChar) {
        prev = prev.getPrevSibling();
      }
      if (isEscapeSequenceStart(prev)) {
        return true;
      }
      final PsiElement parent = aChar.getParent();
      if (prev != null || !(parent instanceof RegExpBranch)) {
        return false;
      }
      final PsiElement grandParent = parent.getParent();
      return grandParent instanceof RegExpPattern && isEscapeSequenceStart(grandParent.getPrevSibling());
    }

    private static boolean isEscapeSequenceStart(@Nullable PsiElement element) {
      return element instanceof PsiWhiteSpace &&
             "\\Q".equals(InjectedLanguageManager.getInstance(element.getProject()).getUnescapedText(element));
    }

    private static boolean isSpace(PsiElement element) {
      if (!(element instanceof RegExpChar aChar)) {
        return false;
      }
      return aChar.getType() == RegExpChar.Type.CHAR && aChar.getValue() == ' ';
    }
  }

  private static class RepeatedSpaceFix extends ModCommandQuickFix {
    private final int myCount;

    RepeatedSpaceFix(int count) {
      myCount = count;
    }

    @Override
    public @Nls @NotNull String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", " {" + myCount + "}");
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return RegExpBundle.message("inspection.quick.fix.replace.with.space.and.repeated.quantifier");
    }

    @Override
    public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof RegExpBranch)) {
        return ModCommand.nop();
      }
      final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(element.getProject());
      final TextRange range = descriptor.getTextRangeInElement();
      final StringBuilder text = new StringBuilder();
      PsiElement child = element.getFirstChild();
      boolean inserted = false;
      while (child != null) {
        if (!range.contains(child.getStartOffsetInParent())) {
          text.append(injectedLanguageManager.getUnescapedText(child));
        }
        else if (!inserted) {
          text.append(" {").append(myCount).append('}');
          inserted = true;
        }
        child = child.getNextSibling();
      }
      return ModCommand.psiUpdate(element, e -> RegExpReplacementUtil.replaceInContext(e, text.toString()));
    }
  }
}
