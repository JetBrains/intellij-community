// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection;

import com.intellij.codeInspection.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.lang.regexp.RegExpBundle;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class RegExpSimplifiableInspection extends LocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly) {
    return new RegExpSimplifiableVisitor(holder);
  }

  private static class RegExpSimplifiableVisitor extends RegExpElementVisitor {
    private final ProblemsHolder myHolder;

    RegExpSimplifiableVisitor(@NotNull ProblemsHolder holder) {
      super();
      myHolder = holder;
    }

    @Override
    public void visitRegExpClass(RegExpClass regExpClass) {
      super.visitRegExpClass(regExpClass);
      final RegExpClassElement[] elements = regExpClass.getElements();
      for (RegExpClassElement element : elements) {
        if (element instanceof RegExpCharRange) {
          final RegExpCharRange range = (RegExpCharRange)element;
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
          if (element instanceof RegExpSimpleClass) {
            final RegExpSimpleClass simpleClass = (RegExpSimpleClass)element;
            final String text = getInverseSimpleClassText(simpleClass);
            if (text != null) {
              // [^\d] -> \D
              registerProblem(regExpClass, text);
            }
          }
          else if (isDigitRange(element)) {
            // [^0-9] -> \D
            registerProblem(regExpClass, "\\D");
          }
        }
        else {
          if (isWordCharClassExpression(elements)) {
            // [^0-9a-zA-Z_] -> \W
            registerProblem(regExpClass, "\\W");
            return;
          }
          for (RegExpClassElement element : elements) {
            if (isDigitRange(element)) {
              // [^0-9abc] ->  [^\dabc]
              registerProblem(element, "\\d");
            }
          }
        }
      }
      else {
        if (elements.length == 1) {
          final RegExpClassElement element = elements[0];
          if (!(element instanceof RegExpCharRange)) {
            if (!(element instanceof RegExpChar) || !"{}().*+?|$".contains(element.getText())) {
              // [a] -> a
              registerProblem(regExpClass, element.getUnescapedText());
            }
          }
          else {
            if (isDigitRange(element)) {
              // [0-9] -> \d
              registerProblem(regExpClass, "\\d");
            }
          }
        }
        else {
          if (isWordCharClassExpression(elements)) {
            // [0-9a-zA-Z_] -> \w
            registerProblem(regExpClass, "\\w");
            return;
          }
          for (RegExpClassElement element : elements) {
            // [0-9abc] -> [\dabc]
            if (isDigitRange(element)) {
              registerProblem(element, "\\d");
            }
          }
        }
      }
    }

    @Override
    public void visitRegExpClosure(RegExpClosure closure) {
      super.visitRegExpClosure(closure);
      ASTNode token = closure.getQuantifier().getToken();
      if (token == null || token.getElementType() != RegExpTT.STAR) {
        return;
      }
      PsiElement sibling = closure.getPrevSibling();
      RegExpAtom atom = closure.getAtom();
      if (sibling instanceof RegExpElement && atom.getClass() == sibling.getClass() && sibling.textMatches(atom) && !containsGroup(atom)) {
        final String text = atom.getUnescapedText() + '+';
        myHolder.registerProblem(closure.getParent(),
                                 TextRange.from(sibling.getStartOffsetInParent(), sibling.getTextLength() + closure.getTextLength()),
                                 RegExpBundle.message("inspection.warning.can.be.simplified", text),
                                 new RegExpSimplifiableFix(text));
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
      if ("Digit".equals(category) || "IsDigit".equals(category)) {
        registerProblem(property, property.isNegated() ? "\\D" : "\\d");
      }
      else if ("Blank".equals(category) || "IsBlank".equals(category)) {
        registerProblem(property, property.isNegated() ? "[^ \\t]" : "[ \\t]");
      }
      else if ("Space".equals(category) || "IsSpace".equals(category) ||
               "IsWhite_Space".equals(category) || "IsWhiteSpace".equals(category)) {
        registerProblem(property, property.isNegated() ? "\\S" : "\\s");
      }
    }

    @Override
    public void visitRegExpQuantifier(RegExpQuantifier quantifier) {
      if (!quantifier.isCounted()) {
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
      myHolder.registerProblem(element,
                               RegExpBundle.message("inspection.warning.can.be.simplified", replacement),
                               new RegExpSimplifiableFix(replacement));
    }

    private static boolean containsGroup(RegExpAtom atom) {
      return atom instanceof RegExpGroup || PsiTreeUtil.findChildOfType(atom, RegExpGroup.class) != null;
    }

    private static boolean isDigitRange(RegExpElement element) {
      if (!(element instanceof RegExpCharRange)) {
        return false;
      }
      final RegExpCharRange charRange = (RegExpCharRange)element;
      final RegExpChar from = charRange.getFrom();
      final RegExpChar to = charRange.getTo();
      return from.getValue() == '0' && to != null && to.getValue() == '9';
    }

    private static boolean isWordCharClassExpression(RegExpClassElement[] elements) {
      if (elements.length != 4) {
        return false;
      }
      boolean lowerCaseChars = false;
      boolean upperCaseChars = false;
      boolean digits = false;
      boolean underscore = false;
      for (RegExpClassElement element : elements) {
        if (element instanceof RegExpChar) {
          final RegExpChar aChar = (RegExpChar)element;
          if (aChar.getValue() == '_') {
            underscore = true;
          }
        }
        else if (element instanceof RegExpSimpleClass) {
          final RegExpSimpleClass simpleClass = (RegExpSimpleClass)element;
          if (simpleClass.getKind() == RegExpSimpleClass.Kind.DIGIT) {
            digits = true;
          }
        }
        else if (element instanceof RegExpCharRange) {
          final RegExpCharRange range = (RegExpCharRange)element;
          final RegExpChar from = range.getFrom();
          final RegExpChar to = range.getTo();
          if (to == null) {
            break;
          }
          final int fromValue = from.getValue();
          final int toValue = to.getValue();
          if (fromValue == '0' && toValue == '9') {
            digits = true;
          }
          else if (fromValue == 'A' && toValue == 'Z') {
            upperCaseChars = true;
          }
          else if (fromValue == 'a' && toValue == 'z') {
            lowerCaseChars = true;
          }
        }
      }
      return underscore && digits && lowerCaseChars && upperCaseChars;
    }

    private static String getInverseSimpleClassText(RegExpSimpleClass simpleClass) {
      switch (simpleClass.getKind()) {
        case DIGIT:
          return "\\D";
        case NON_DIGIT:
          return "\\d";
        case WORD:
          return "\\W";
        case NON_WORD:
          return "\\w";
        case SPACE:
          return "\\S";
        case NON_SPACE:
          return "\\s";
        case HORIZONTAL_SPACE:
          return "\\H";
        case NON_HORIZONTAL_SPACE:
          return "\\h";
        case VERTICAL_SPACE:
          return "\\V";
        case NON_VERTICAL_SPACE:
          return "\\v";
        case XML_NAME_START:
          return "\\I";
        case NON_XML_NAME_START:
          return "\\i";
        case XML_NAME_PART:
          return "\\C";
        case NON_XML_NAME_PART:
          return "\\c";
        default:
          return null;
      }
    }

    private static class RegExpSimplifiableFix implements LocalQuickFix {
      private final String myExpression;
      private final boolean myDelete;

      RegExpSimplifiableFix(String newExpression) {
        this(newExpression, false);
      }

      RegExpSimplifiableFix(String expression, boolean delete) {
        myExpression = expression;
        myDelete = delete;
      }

      @Override
      public @NotNull String getFamilyName() {
        return CommonQuickFixBundle.message("fix.simplify");
      }

      @Override
      public @NotNull String getName() {
        return myDelete
               ? CommonQuickFixBundle.message("fix.remove", myExpression)
               : CommonQuickFixBundle.message("fix.replace.with.x", myExpression);
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof RegExpElement)) {
          return;
        }
        RegExpReplacementUtil.replaceInContext(element, myDelete ? "" : myExpression, descriptor.getTextRangeInElement());
      }
    }
  }
}
