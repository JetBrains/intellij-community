// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.regexp.RegExpBundle;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class SingleCharAlternationInspection extends LocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new SingleCharAlternationVisitor(holder);
  }

  private static class SingleCharAlternationVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;

    SingleCharAlternationVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitRegExpPattern(RegExpPattern pattern) {
      final RegExpBranch[] branches = pattern.getBranches();
      if (branches.length < 2) {
        return;
      }
      if (!ContainerUtil.and(branches, SingleCharAlternationVisitor::isSingleChar)) {
        return;
      }
      final String text = buildReplacementText(pattern);
      myHolder.registerProblem(pattern, RegExpBundle.message("inspection.warning.single.character.alternation.in.regexp"),
                               new SingleCharAlternationFix(text));
    }

    private static boolean isSingleChar(RegExpBranch branch) {
      final RegExpAtom[] atoms = branch.getAtoms();
      return atoms.length == 1 && atoms[0] instanceof RegExpChar;
    }

    private static class SingleCharAlternationFix extends PsiUpdateModCommandQuickFix {

      private final String myText;

      SingleCharAlternationFix(String text) {
        myText = text;
      }

      @Override
      public @Nls @NotNull String getName() {
        return CommonQuickFixBundle.message("fix.replace.with.x", myText);
      }

      @Override
      public @Nls @NotNull String getFamilyName() {
        return RegExpBundle.message("inspection.quick.fix.replace.alternation.with.character.class");
      }

      @Override
      protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
        if (!(element instanceof RegExpPattern pattern)) {
          return;
        }
        final PsiElement parent = pattern.getParent();
        final PsiElement victim =
          (parent instanceof RegExpGroup group && group.getType() == RegExpGroup.Type.NON_CAPTURING) ? parent : pattern;
        final String replacementText = buildReplacementText(pattern);
        if (replacementText == null) {
          return;
        }
        RegExpReplacementUtil.replaceInContext(victim, replacementText);
      }
    }
  }

  static String buildReplacementText(RegExpPattern pattern) {
    final StringBuilder text = new StringBuilder("[");
    for (RegExpBranch branch : pattern.getBranches()) {
      for (PsiElement child : branch.getChildren()) {
        if (!(child instanceof RegExpChar ch)) {
          return null;
        }
        final IElementType type = ch.getNode().getFirstChildNode().getElementType();
        if (type == RegExpTT.REDUNDANT_ESCAPE) {
          final int value = ch.getValue();
          if (value == ']') {
            text.append(ch.getUnescapedText());
          }
          else if (value == '-' && text.length() != 1) {
            text.append("\\-");
          }
          else {
            text.append((char)value);
          }
        }
        else if (type == RegExpTT.ESC_CHARACTER) {
          final int value = ch.getValue();
          switch (value) {
            case '.', '$', '?', '*', '+', '|', '{', '(', ')' -> text.append((char)value);
            case '^' -> {
              if (text.length() == 1) {
                text.append(ch.getUnescapedText());
              }
              else {
                text.append((char)value);
              }
            }
            default -> text.append(ch.getUnescapedText());
          }
        }
        else {
          final int value = ch.getValue();
          switch (value) {
            case ']':
              text.append("\\]");
              break;
            case '-':
              if (text.length() != 1) {
                text.append("\\-");
                break;
              }
            default:
              text.append(ch.getUnescapedText());
          }
        }
      }
    }
    text.append("]");
    return text.toString();
  }
}
