// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.NotNull;

public final class EscapedSpaceInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightingFeature.TEXT_BLOCK_ESCAPES.isAvailable(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitFragment(@NotNull PsiFragment fragment) {
        check(fragment, fragment.isTextBlock());
      }

      @Override
      public void visitLiteralExpression(@NotNull PsiLiteralExpression literal) {
        PsiType type = literal.getType();
        if (!TypeUtils.isJavaLangString(type) && !PsiTypes.charType().equals(type)) return;
        check(literal, literal.isTextBlock());
      }

      private void check(@NotNull PsiElement fragment, boolean textBlock) {
        for (int pos : findPositions(fragment.getText(), textBlock)) {
          holder.registerProblem(fragment, TextRange.create(pos, pos + 2),
                                 textBlock
                                 ? InspectionGadgetsBundle.message("inspection.use.of.slash.s.message")
                                 : InspectionGadgetsBundle.message("inspection.use.of.slash.s.non.text.block.message"),
                                 new ReplaceWithSpaceFix());
        }
      }
    };
  }

  private static int[] findPositions(String text, boolean block) {
    int pos = 1;
    IntList list = new IntArrayList();
    while (true) {
      pos = text.indexOf('\\', pos);
      if (pos == -1 || pos == text.length() - 1) break;
      char next = text.charAt(pos + 1);
      if (next == 'u') {
        // unicode escape can contain several 'u', according to the spec
        pos += 2;
        while (pos < text.length() && text.charAt(pos) == 'u') pos++;
        pos += 4;
        continue;
      }
      pos += 2;
      if (next >= '0' && next <= '9') {
        // octal escape
        if (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') pos++;
        if (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') pos++;
        continue;
      }
      if (next != 's') {
        // other escapes
        continue;
      }
      if (pos > 4 && text.startsWith("\\s", pos - 4)) continue;
      if (text.startsWith("\\s", pos)) continue;
      if (block && (pos == text.length() || text.charAt(pos) == '\n'
                    || pos == text.length() - 3 && text.endsWith("\"\"\""))) {
        continue;
      }
      list.add(pos - 2);
    }
    return list.toIntArray();
  }

  private static class ReplaceWithSpaceFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.use.of.slash.s.fix.family");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      String text = element.getText();
      int[] positions;
      if (element instanceof PsiLiteralExpression literal) {
        positions = findPositions(text, literal.isTextBlock());
      }
      else if (element instanceof PsiFragment fragment) {
        positions = findPositions(text, fragment.isTextBlock());
      }
      else {
        return;
      }
      String newText = IntStreamEx.of(positions)
        .takeWhile(pos -> pos < text.length() - 2)
        .boxed()
        .prepend(-2)
        .append(text.length())
        .pairMap((start, end) -> text.substring(start + 2, end))
        .joining(" ");
      if (element instanceof PsiFragment) {
        PsiReplacementUtil.replaceFragment((PsiFragment)element, newText);
      }
      else {
        element.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(newText, null));
      }
    }
  }
}