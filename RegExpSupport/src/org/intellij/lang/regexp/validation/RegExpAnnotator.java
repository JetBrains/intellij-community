/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.regexp.validation;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.regexp.RegExpLanguageHosts;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

public final class RegExpAnnotator extends RegExpElementVisitor implements Annotator {
  private static final Set<String> POSIX_CHARACTER_CLASSES = ContainerUtil.newHashSet(
    "alnum", "alpha", "ascii", "blank", "cntrl", "digit", "graph", "lower", "print", "punct", "space", "upper", "word", "xdigit");
  private static final String ILLEGAL_CHARACTER_RANGE_TO_FROM = "Illegal character range (to < from)";
  private AnnotationHolder myHolder;
  private final RegExpLanguageHosts myLanguageHosts;

  public RegExpAnnotator() {
    myLanguageHosts = RegExpLanguageHosts.getInstance();
  }

  @Override
  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    assert myHolder == null : "unsupported concurrent annotator invocation";
    try {
      myHolder = holder;
      psiElement.accept(this);
    }
    finally {
      myHolder = null;
    }
  }

  @Override
  public void visitRegExpOptions(RegExpOptions options) {
    checkValidFlag(options.getOptionsOn(), options);
    checkValidFlag(options.getOptionsOff(), options);
  }

  private void checkValidFlag(@Nullable ASTNode optionsNode, @NotNull RegExpOptions context) {
    if (optionsNode == null) {
      return;
    }
    final String text = optionsNode.getText();
    final int start = (optionsNode.getElementType() == RegExpTT.OPTIONS_OFF) ? 1 : 0; // skip '-' if necessary
    for (int i = start, length = text.length(); i < length; i++) {
      final int c = text.codePointAt(i);
      if (!Character.isBmpCodePoint(c) || !myLanguageHosts.supportsInlineOptionFlag((char)c, context)) {
        final int offset = optionsNode.getStartOffset() + i;
        myHolder.createErrorAnnotation(new TextRange(offset, offset + 1), "Unknown inline option flag");
      }
    }
  }

  @Override
  public void visitRegExpCharRange(RegExpCharRange range) {
    final RegExpCharRange.Endpoint from = range.getFrom();
    final RegExpCharRange.Endpoint to = range.getTo();
    if (from instanceof RegExpChar && to instanceof RegExpChar) {
      final Character t = ((RegExpChar)to).getValue();
      final Character f = ((RegExpChar)from).getValue();
      if (t != null && f != null) {
        if (t < f) {
          if (handleSurrogates(range, f, t)) return;
          myHolder.createErrorAnnotation(range, ILLEGAL_CHARACTER_RANGE_TO_FROM);
        }
        else if (t == f) {
          myHolder.createWarningAnnotation(range, "Redundant character range");
        }
      }
    }
    else if (to instanceof RegExpSimpleClass) {
      myHolder.createErrorAnnotation(to, "Character class not allowed inside character range");
    }
    else if (from.getText().equals(to.getText())) {
      myHolder.createWarningAnnotation(range, "Redundant character range");
    }
  }

  private boolean handleSurrogates(RegExpCharRange range, Character f, Character t) {
    // \ud800\udc00-\udbff\udfff
    PsiElement prevSibling = range.getPrevSibling();
    PsiElement nextSibling = range.getNextSibling();

    if (prevSibling instanceof RegExpChar && nextSibling instanceof RegExpChar) {
      Character prevSiblingValue = ((RegExpChar)prevSibling).getValue();
      Character nextSiblingValue = ((RegExpChar)nextSibling).getValue();

      if (prevSiblingValue != null && nextSiblingValue != null &&
          Character.isSurrogatePair(prevSiblingValue, f) && Character.isSurrogatePair(t, nextSiblingValue)) {
        if (Character.toCodePoint(prevSiblingValue, f) > Character.toCodePoint(t, nextSiblingValue)) {
          TextRange prevSiblingRange = prevSibling.getTextRange();
          TextRange nextSiblingRange = nextSibling.getTextRange();
          TextRange errorRange = new TextRange(prevSiblingRange.getStartOffset(), nextSiblingRange.getEndOffset());
          myHolder.createErrorAnnotation(errorRange, ILLEGAL_CHARACTER_RANGE_TO_FROM);
        }
        return true;
      }
    }
    return false;
  }

  @Override
  public void visitRegExpBoundary(RegExpBoundary boundary) {
    if (!myLanguageHosts.supportsBoundary(boundary)) {
      myHolder.createErrorAnnotation(boundary, "Unsupported boundary");
    }
  }

  @Override
  public void visitSimpleClass(RegExpSimpleClass simpleClass) {
    if (!myLanguageHosts.supportsSimpleClass(simpleClass)) {
      myHolder.createErrorAnnotation(simpleClass, "Illegal/unsupported escape sequence");
    }
  }

  @Override
  public void visitRegExpClass(RegExpClass regExpClass) {
    if (!(regExpClass.getParent() instanceof RegExpClass)) {
      checkForDuplicates(regExpClass, new HashSet<>());
    }
  }

  private void checkForDuplicates(RegExpClassElement element, Set<Object> seen) {
    if (element instanceof RegExpChar) {
      final RegExpChar regExpChar = (RegExpChar)element;
      final Character value = regExpChar.getValue();
      if (value != null && !seen.add(value)) {
        myHolder.createWarningAnnotation(regExpChar, "Duplicate character '" + regExpChar.getText() + "' inside character class");
      }
    }
    else if (element instanceof RegExpSimpleClass) {
      final RegExpSimpleClass regExpSimpleClass = (RegExpSimpleClass)element;
      final RegExpSimpleClass.Kind kind = regExpSimpleClass.getKind();
      if (!seen.add(kind)) {
        myHolder.createWarningAnnotation(regExpSimpleClass, "Duplicate predefined character class '" + regExpSimpleClass.getText() +
                                                            "' inside character class");
      }
    }
    else if (element instanceof RegExpClass) {
      final RegExpClass regExpClass = (RegExpClass)element;
      for (RegExpClassElement classElement : regExpClass.getElements()) {
        checkForDuplicates(classElement, seen);
      }
    }
    else if (element instanceof RegExpUnion) {
      final RegExpUnion union = (RegExpUnion)element;
      for (RegExpClassElement classElement : union.getElements()) {
        checkForDuplicates(classElement, seen);
      }
    }
  }

  @Override
  public void visitRegExpChar(final RegExpChar ch) {
    final PsiElement child = ch.getFirstChild();
    IElementType type = child.getNode().getElementType();
    if (type == StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN) {
      myHolder.createErrorAnnotation(ch, "Illegal/unsupported escape sequence");
      return;
    }
    else if (type == RegExpTT.BAD_HEX_VALUE) {
      myHolder.createErrorAnnotation(ch, "Illegal hexadecimal escape sequence");
      return;
    }
    else if (type == RegExpTT.BAD_OCT_VALUE) {
      myHolder.createErrorAnnotation(ch, "Illegal octal escape sequence");
      return;
    }
    else if (type == StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN) {
      myHolder.createErrorAnnotation(ch, "Illegal unicode escape sequence");
      return;
    }
    final String text = ch.getUnescapedText();
    if (type == RegExpTT.ESC_CTRL_CHARACTER && text.equals("\\b") && !myLanguageHosts.supportsLiteralBackspace(ch)) {
      myHolder.createErrorAnnotation(ch, "Illegal/unsupported escape sequence");
    }
    if (text.startsWith("\\") && myLanguageHosts.isRedundantEscape(ch, text)) {
      final ASTNode astNode = ch.getNode().getFirstChildNode();
      if (astNode != null && astNode.getElementType() == RegExpTT.REDUNDANT_ESCAPE) {
        final Annotation a = myHolder.createWeakWarningAnnotation(ch, "Redundant character escape");
        registerFix(a, new RemoveRedundantEscapeAction(ch));
      }
    }
    final RegExpChar.Type charType = ch.getType();
    if (charType == RegExpChar.Type.HEX || charType == RegExpChar.Type.UNICODE) {
      if (ch.getValue() == null) {
        myHolder.createErrorAnnotation(ch, "Illegal unicode escape sequence");
        return;
      }
      if (text.charAt(text.length() - 1) == '}') {
        if (!myLanguageHosts.supportsExtendedHexCharacter(ch)) {
          myHolder.createErrorAnnotation(ch, "This hex character syntax is not supported");
        }
      }
    }
  }

  @Override
  public void visitRegExpProperty(RegExpProperty property) {
    final ASTNode category = property.getCategoryNode();
    if (category == null) {
      return;
    }
    if(!myLanguageHosts.isValidCategory(category.getPsi(), category.getText())) {
      final Annotation a = myHolder.createErrorAnnotation(category, "Unknown character category");
      a.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
    }
  }

  @Override
  public void visitRegExpNamedCharacter(RegExpNamedCharacter namedCharacter) {
    if (!myLanguageHosts.supportsNamedCharacters(namedCharacter)) {
      myHolder.createErrorAnnotation(namedCharacter, "Named Unicode characters are not allowed in this regular expression dialect");
    }
    else if (!myLanguageHosts.isValidNamedCharacter(namedCharacter)) {
      final ASTNode node = namedCharacter.getNameNode();
      if (node != null) {
        final Annotation a = myHolder.createErrorAnnotation(node, "Unknown character name");
        a.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
      }
    }
  }

  @Override
  public void visitRegExpBackref(final RegExpBackref backref) {
    final RegExpGroup group = backref.resolve();
    if (group == null) {
      final Annotation a = myHolder.createErrorAnnotation(backref, "Unresolved back reference");
      a.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
    }
    else if (PsiTreeUtil.isAncestor(group, backref, true)) {
      myHolder.createWarningAnnotation(backref, "Back reference is nested into the capturing group it refers to");
    }
  }

  @Override
  public void visitRegExpIntersection(RegExpIntersection intersection) {
    if (intersection.getOperands().length == 0) {
      myHolder.createErrorAnnotation(intersection, "Illegal empty intersection");
    }
  }

  @Override
  public void visitRegExpGroup(RegExpGroup group) {
    final RegExpPattern pattern = group.getPattern();
    if (pattern != null) {
      final RegExpBranch[] branches = pattern.getBranches();
      if (isEmpty(branches)) {
        // catches "()" as well as "(|)"
        myHolder.createWarningAnnotation(group, "Empty group");
      }
      else if (branches.length == 1) {
        final RegExpAtom[] atoms = branches[0].getAtoms();
        if (atoms.length == 1 && atoms[0] instanceof RegExpGroup) {
          if (group.isSimple()) {
            final RegExpGroup innerGroup = (RegExpGroup)atoms[0];
            if (group.isCapturing() == innerGroup.isCapturing()) {
              myHolder.createWarningAnnotation(group, "Redundant group nesting");
            }
          }
        }
      }
    }
    if (group.isPythonNamedGroup() || group.isRubyNamedGroup()) {
      if (!myLanguageHosts.supportsNamedGroupSyntax(group)) {
        myHolder.createErrorAnnotation(group, "This named group syntax is not supported");
      }
    }
    final String name = group.getName();
    if (name != null && !myLanguageHosts.isValidGroupName(name, group)) {
      final ASTNode node = group.getNode().findChildByType(RegExpTT.NAME);
      if (node != null) myHolder.createErrorAnnotation(node, "Invalid group name");
    }
  }

  @Override
  public void visitRegExpNamedGroupRef(RegExpNamedGroupRef groupRef) {
    if (!myLanguageHosts.supportsNamedGroupRefSyntax(groupRef)) {
      myHolder.createErrorAnnotation(groupRef, "This named group reference syntax is not supported");
      return;
    }
    if (groupRef.getGroupName() == null) {
      return;
    }
    final RegExpGroup group = groupRef.resolve();
    if (group == null) {
      final ASTNode node = groupRef.getNode().findChildByType(RegExpTT.NAME);
      if (node != null) {
        final Annotation a = myHolder.createErrorAnnotation(node, "Unresolved named group reference");
        a.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
      }
    }
    else if (PsiTreeUtil.isAncestor(group, groupRef, true)) {
      myHolder.createWarningAnnotation(groupRef, "Group reference is nested into the named group it refers to");
    }
  }

  @Override
  public void visitComment(PsiComment comment) {
    if (comment.getText().startsWith("(?#")) {
      if (!myLanguageHosts.supportsPerl5EmbeddedComments(comment)) {
        myHolder.createErrorAnnotation(comment, "Embedded comments are not supported");
      }
    }
  }

  @Override
  public void visitRegExpPyCondRef(RegExpPyCondRef condRef) {
    if (!myLanguageHosts.supportsPythonConditionalRefs(condRef)) {
      myHolder.createErrorAnnotation(condRef, "Conditional references are not supported");
    }
  }

  private static boolean isEmpty(RegExpBranch[] branches) {
    for (RegExpBranch branch : branches) {
      if (branch.getAtoms().length > 0) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void visitRegExpQuantifier(RegExpQuantifier quantifier) {
    final RegExpQuantifier.Count count = quantifier.getCount();
    if (!(count instanceof RegExpQuantifier.SimpleCount)) {
      String min = count.getMin();
      String max = count.getMax();
      if (max.equals(min)) {
        if ("1".equals(max)) { // TODO: is this safe when reluctant or possessive modifier is present?
          final Annotation a = myHolder.createWeakWarningAnnotation(quantifier, "Single repetition");
          registerFix(a, new SimplifyQuantifierAction(quantifier, null));
        }
        else {
          final ASTNode node = quantifier.getNode();
          if (node.findChildByType(RegExpTT.COMMA) != null) {
            final Annotation a = myHolder.createWeakWarningAnnotation(quantifier, "Fixed repetition range");
            registerFix(a, new SimplifyQuantifierAction(quantifier, "{" + max + "}"));
          }
        }
      }
      else if ("0".equals(min) && "1".equals(max)) {
        final Annotation a = myHolder.createWeakWarningAnnotation(quantifier, "Repetition range replaceable by '?'");
        registerFix(a, new SimplifyQuantifierAction(quantifier, "?"));
      }
      else if ("0".equals(min) && max.isEmpty()) {
        final Annotation a = myHolder.createWeakWarningAnnotation(quantifier, "Repetition range replaceable by '*'");
        registerFix(a, new SimplifyQuantifierAction(quantifier, "*"));
      }
      else if ("1".equals(min) && max.isEmpty()) {
        final Annotation a = myHolder.createWeakWarningAnnotation(quantifier, "Repetition range replaceable by '+'");
        registerFix(a, new SimplifyQuantifierAction(quantifier, "+"));
      }
      else if (!min.isEmpty() && !max.isEmpty()) {
        try {
          BigInteger minInt = new BigInteger(min);
          BigInteger maxInt = new BigInteger(max);
          if (maxInt.compareTo(minInt) < 0) {
            myHolder.createErrorAnnotation(quantifier, "Illegal repetition range");
          }
        }
        catch (NumberFormatException ex) {
          myHolder.createErrorAnnotation(quantifier, "Illegal repetition value");
        }
      }
    }
    if (quantifier.getType() == RegExpQuantifier.Type.POSSESSIVE) {
      if (!myLanguageHosts.supportsPossessiveQuantifiers(quantifier)) {
        myHolder.createErrorAnnotation(quantifier, "Nested quantifier in regexp");
      }
    }
  }

  @Override
  public void visitPosixBracketExpression(RegExpPosixBracketExpression posixBracketExpression) {
    final String className = posixBracketExpression.getClassName();
    if (!POSIX_CHARACTER_CLASSES.contains(className)) {
      final ASTNode node = posixBracketExpression.getNode().findChildByType(RegExpTT.NAME);
      if (node != null) {
        final Annotation annotation = myHolder.createErrorAnnotation(node, "Unknown POSIX character class");
        annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
      }
    }
  }

  private static void registerFix(Annotation a, IntentionAction action) {
    if (a != null) {
      // IDEA-9381
      a.registerFix(action);
    }
  }

}
