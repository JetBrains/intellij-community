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
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.tree.IElementType;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

/**
 * @author Bas Leijdekkers
 */
public class SingleCharAlternationInspection extends LocalInspectionTool {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Single character alternation";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new SingleCharAlternationVisitor(holder);
  }

  private static class SingleCharAlternationVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;

    public SingleCharAlternationVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitRegExpPattern(RegExpPattern pattern) {
      final RegExpBranch[] branches = pattern.getBranches();
      if (branches.length < 2) {
        return;
      }
      if (!Stream.of(branches).allMatch(SingleCharAlternationVisitor::isSingleChar)) {
        return;
      }
      final String text = buildReplacementText(pattern);
      //noinspection DialogTitleCapitalization
      myHolder.registerProblem(pattern, "Single character alternation in RegExp", new SingleCharAlternationFix(text));
    }

    private static boolean isSingleChar(RegExpBranch branch) {
      final RegExpAtom[] atoms = branch.getAtoms();
      return atoms.length == 1 && atoms[0] instanceof RegExpChar;
    }

    private static class SingleCharAlternationFix implements LocalQuickFix {

      private final String myText;

      public SingleCharAlternationFix(String text) {
        myText = text;
      }

      @Nls
      @NotNull
      @Override
      public String getName() {
        return "Replace with '" + myText + '\'';
      }

      @Nls
      @NotNull
      @Override
      public String getFamilyName() {
        return "Replace alternation with character class";
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof RegExpPattern)) {
          return;
        }
        final RegExpPattern pattern = (RegExpPattern)element;
        final String text = buildReplacementText(pattern);
        final RegExpBranch branch = RegExpFactory.createBranchFromText(text, element);
        final PsiElement parent = pattern.getParent();
        if (parent instanceof RegExpGroup && ((RegExpGroup)parent).getType() == RegExpGroup.Type.NON_CAPTURING) {
          parent.replace(branch.getAtoms()[0]);
        }
        else {
          pattern.replace(branch.getAtoms()[0]);
        }
      }
    }
  }

  static String buildReplacementText(RegExpPattern pattern) {
    final StringBuilder text = new StringBuilder("[");
    for (RegExpBranch branch : pattern.getBranches()) {
      for (PsiElement child : branch.getChildren()) {
        final RegExpChar ch = (RegExpChar)child;
        final IElementType type = ch.getNode().getFirstChildNode().getElementType();
        if (type == RegExpTT.REDUNDANT_ESCAPE) {
          text.append((char)ch.getValue());
        }
        else if (type == RegExpTT.ESC_CHARACTER) {
          final int value = ch.getValue();
          switch (value) {
            case '.':
            case '$':
            case '?':
            case '*':
            case '+':
            case '|':
            case '{':
            case '(':
            case ')':
              text.append((char)value);
              break;
            case '^':
              if (text.length() == 1) {
                text.append(ch.getUnescapedText());
              }
              else {
                text.append((char)value);
              }
              break;
            default:
              text.append(ch.getUnescapedText());
          }
        }
        else {
          final int value = ch.getValue();
          switch (value) {
            case ']':
              text.append("\\]");
              break;
            case '-':
            case '^':
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
    return RegExpReplacementUtil.escapeForContext(text.toString(), pattern);
  }
}
