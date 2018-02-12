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
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.RegExpChar;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class OctalEscapeInspection extends LocalInspectionTool {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Octal escape";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new OctalEscapeVisitor(holder);
  }

  private static class OctalEscapeVisitor extends RegExpElementVisitor {

    private final ProblemsHolder myHolder;

    public OctalEscapeVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitRegExpChar(RegExpChar ch) {
      if (ch.getType() != RegExpChar.Type.OCT) {
        return;
      }
      myHolder.registerProblem(ch, "Octal escape <code>#ref</code> in RegExp", new ReplaceWithHexEscapeFix(buildReplacementText(ch)));
    }
  }

  static String buildReplacementText(RegExpChar aChar) {
    final int value = aChar.getValue();
    final String hex = Integer.toHexString(value);
    final String result = (hex.length() == 1 ? "\\x0" : "\\x") + hex;
    return RegExpReplacementUtil.escapeForContext(result, aChar);
  }

  private static class ReplaceWithHexEscapeFix implements LocalQuickFix {
    private final String myHex;

    public ReplaceWithHexEscapeFix(String hex) {
      myHex = hex;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Replace with '" + myHex + '\'';
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with hexadecimal escape";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof RegExpChar)) {
        return;
      }
      final ASTNode node = element.getNode();
      final RegExpChar aChar = (RegExpChar)element;
      final ASTNode anchor = node.getFirstChildNode();
      node.addLeaf(RegExpTT.HEX_CHAR, buildReplacementText(aChar), anchor);
      node.removeChild(anchor);
    }
  }
}