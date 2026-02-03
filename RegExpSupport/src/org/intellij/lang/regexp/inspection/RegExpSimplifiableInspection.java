// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.lang.regexp.RegExpBundle;
import org.intellij.lang.regexp.RegExpCapability;
import org.intellij.lang.regexp.RegExpLanguageHosts;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.RegExpAtom;
import org.intellij.lang.regexp.psi.RegExpChar;
import org.intellij.lang.regexp.psi.RegExpCharRange;
import org.intellij.lang.regexp.psi.RegExpClass;
import org.intellij.lang.regexp.psi.RegExpClassElement;
import org.intellij.lang.regexp.psi.RegExpClosure;
import org.intellij.lang.regexp.psi.RegExpElement;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.intellij.lang.regexp.psi.RegExpIntersection;
import org.intellij.lang.regexp.psi.RegExpNumber;
import org.intellij.lang.regexp.psi.RegExpPosixBracketExpression;
import org.intellij.lang.regexp.psi.RegExpProperty;
import org.intellij.lang.regexp.psi.RegExpQuantifier;
import org.intellij.lang.regexp.psi.RegExpSimpleClass;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class RegExpSimplifiableInspection extends LocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new RegExpSimplifiableVisitor(holder);
  }

  private static class RegExpSimplifiableVisitor extends RegExpElementVisitor {
    private final ProblemsHolder myHolder;

    RegExpSimplifiableVisitor(@NotNull ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitRegExpClass(RegExpClass regExpClass) {
      super.visitRegExpClass(regExpClass);
      if (regExpClass.getLastChild() instanceof PsiErrorElement) return;
      final RegExpClassElement[] elements = regExpClass.getElements();
      for (RegExpClassElement element : elements) {
        if (element instanceof RegExpCharRange range) {
          final int from = range.getFrom().getValue();
          final RegExpChar to = range.getTo();
          if (from != -1 && to != null && from == to.getValue()) {
            // [a-abc] -> [abc]
            registerProblem(range, to.getUnescapedText());
          }
        }
      }
      if (regExpClass.isNegated()) {
        if (elements.length == 1) {
          final RegExpClassElement element = elements[0];
          if (element instanceof RegExpSimpleClass simpleClass) {
            final String text = getInverseSimpleClassText(simpleClass);
            if (text != null) {
              // [^\d] -> \D
              registerProblem(regExpClass, text);
            }
          }
        }
      }
      else {
        if (elements.length == 1) {
          final RegExpClassElement element = elements[0];
          if (element.getLastChild() instanceof PsiErrorElement || element instanceof RegExpPosixBracketExpression) return;
          if (!(element instanceof RegExpCharRange) && !(element instanceof RegExpIntersection)) {
            if (!(element instanceof RegExpChar) || !"[{}().*+?|$".contains(element.getText())) {
              final String text = element.getUnescapedText();
              if (StringUtil.isWhiteSpace(text.charAt(0)) && isCommentMode(element)) return;
              if (text.equals("\\b")) return;
              // [a] -> a
              registerProblem(regExpClass, text);
            }
          }
        }
      }
    }

    private static boolean isCommentMode(@NotNull RegExpElement element) {
      return RegExpLanguageHosts.getInstance().getCapabilities(element).contains(RegExpCapability.COMMENT_MODE);
    }

    @Override
    public void visitRegExpClosure(RegExpClosure closure) {
      super.visitRegExpClosure(closure);
      final ASTNode token = closure.getQuantifier().getToken();
      if (token == null || token.getElementType() != RegExpTT.STAR) {
        return;
      }
      final PsiElement sibling = closure.getPrevSibling();
      final RegExpAtom atom = closure.getAtom();
      if (sibling instanceof RegExpElement && atom.getClass() == sibling.getClass() && sibling.textMatches(atom) && !containsGroup(atom)) {
        final String text = atom.getUnescapedText() + '+';
        final String escaped = RegExpReplacementUtil.escapeForContext(text, closure.getContainingFile());
        myHolder.registerProblem(closure.getParent(),
                                 TextRange.from(sibling.getStartOffsetInParent(), sibling.getTextLength() + closure.getTextLength()),
                                 RegExpBundle.message("inspection.warning.can.be.simplified", escaped),
                                 new RegExpSimplifiableFix(text, escaped));
      }
    }

    @Override
    public void visitRegExpProperty(RegExpProperty property) {
      super.visitRegExpProperty(property);
      final ASTNode categoryNode = property.getCategoryNode();
      if (categoryNode == null) {
        return;
      }
      final String category = categoryNode.getText();
      switch (category) {
        case "Digit", "IsDigit" -> registerProblem(property, property.isNegated() ? "\\D" : "\\d");
        case "Blank", "IsBlank" -> registerProblem(property, property.isNegated() ? "[^ \\t]" : "[ \\t]");
        case "Space", "IsSpace", "IsWhite_Space", "IsWhiteSpace" -> registerProblem(property, property.isNegated() ? "\\S" : "\\s");
      }
    }

    @Override
    public void visitRegExpQuantifier(RegExpQuantifier quantifier) {
      if (!quantifier.isCounted() || quantifier.getLastChild() instanceof PsiErrorElement) {
        return;
      }
      final RegExpNumber minElement = quantifier.getMin();
      final String min = minElement == null ? "" : minElement.getText();
      final RegExpNumber maxElement = quantifier.getMax();
      final String max = maxElement == null ? "" : maxElement.getText();
      if (!max.isEmpty() && max.equals(min)) {
        if ("1".equals(max)) {
          myHolder.registerProblem(quantifier,
                                   RegExpBundle.message("inspection.warning.can.be.removed"),
                                   new RegExpSimplifiableFix(quantifier.getText(), true));
        }
        else {
          final ASTNode node = quantifier.getNode();
          if (node.findChildByType(RegExpTT.COMMA) != null) {
            registerProblem(quantifier, "{" + max + "}");
          }
        }
      }
      else if (("0".equals(min) || min.isEmpty()) && "1".equals(max)) {
        registerProblem(quantifier, "?");
      }
      else if (("0".equals(min) || min.isEmpty()) && max.isEmpty()) {
        registerProblem(quantifier, "*");
      }
      else if ("1".equals(min) && max.isEmpty()) {
        registerProblem(quantifier, "+");
      }
    }

    private void registerProblem(RegExpElement element, String replacement) {
      final String escaped = RegExpReplacementUtil.escapeForContext(replacement, element.getContainingFile());
      myHolder.registerProblem(element,
                               RegExpBundle.message("inspection.warning.can.be.simplified", escaped),
                               new RegExpSimplifiableFix(replacement, escaped));
    }

    private static boolean containsGroup(RegExpAtom atom) {
      return atom instanceof RegExpGroup || PsiTreeUtil.findChildOfType(atom, RegExpGroup.class) != null;
    }

    private static String getInverseSimpleClassText(RegExpSimpleClass simpleClass) {
      return switch (simpleClass.getKind()) {
        case DIGIT -> "\\D";
        case NON_DIGIT -> "\\d";
        case WORD -> "\\W";
        case NON_WORD -> "\\w";
        case SPACE -> "\\S";
        case NON_SPACE -> "\\s";
        case HORIZONTAL_SPACE -> "\\H";
        case NON_HORIZONTAL_SPACE -> "\\h";
        case VERTICAL_SPACE -> "\\V";
        case NON_VERTICAL_SPACE -> "\\v";
        case XML_NAME_START -> "\\I";
        case NON_XML_NAME_START -> "\\i";
        case XML_NAME_PART -> "\\C";
        case NON_XML_NAME_PART -> "\\c";
        default -> null;
      };
    }

    private static class RegExpSimplifiableFix extends ModCommandQuickFix {
      private final String myExpression;
      private final String myPresentableExpression;
      private final boolean myDelete;

      RegExpSimplifiableFix(String newExpression, String presentableExpression) {
        myExpression = newExpression;
        myPresentableExpression = presentableExpression;
        myDelete = false;
      }

      RegExpSimplifiableFix(String expression, boolean delete) {
        myExpression = myPresentableExpression = expression;
        myDelete = delete;
      }

      @Override
      public @NotNull String getFamilyName() {
        return CommonQuickFixBundle.message("fix.simplify");
      }

      @Override
      public @NotNull String getName() {
        return myDelete
               ? CommonQuickFixBundle.message("fix.remove", myPresentableExpression)
               : CommonQuickFixBundle.message("fix.replace.with.x", myPresentableExpression);
      }

      @Override
      public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof RegExpElement)) {
          return ModCommand.nop();
        }
        return ModCommand.psiUpdate(element, e ->
          RegExpReplacementUtil.replaceInContext(e, myDelete ? "" : myExpression, descriptor.getTextRangeInElement()));
      }
    }
  }
}
