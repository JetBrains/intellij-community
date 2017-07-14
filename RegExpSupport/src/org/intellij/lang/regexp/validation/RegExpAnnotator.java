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
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.regexp.RegExpLanguageHost;
import org.intellij.lang.regexp.RegExpLanguageHosts;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class RegExpAnnotator extends RegExpElementVisitor implements Annotator {
  private static final Set<String> POSIX_CHARACTER_CLASSES = ContainerUtil.newHashSet(
    "alnum", "alpha", "ascii", "blank", "cntrl", "digit", "graph", "lower", "print", "punct", "space", "upper", "word", "xdigit");
  private AnnotationHolder myHolder;
  private final RegExpLanguageHosts myLanguageHosts;
  private final Key<Map<String, RegExpGroup>> NAMED_GROUP_MAP = new Key<>("REG_EXP_NAMED_GROUP_MAP");

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
  public void visitRegExpSetOptions(RegExpSetOptions options) {
    checkValidFlag(options.getOnOptions(), false);
    checkValidFlag(options.getOffOptions(), true);
  }

  private void checkValidFlag(@Nullable RegExpOptions options, boolean skipMinus) {
    if (options == null) {
      return;
    }
    final String text = options.getText();
    final int start = skipMinus ? 1 : 0; // skip '-' if necessary
    for (int i = start, length = text.length(); i < length; i++) {
      final int c = text.codePointAt(i);
      if (!Character.isBmpCodePoint(c) || !myLanguageHosts.supportsInlineOptionFlag((char)c, options)) {
        final int offset = options.getTextOffset() + i;
        myHolder.createErrorAnnotation(new TextRange(offset, offset + 1), "Unknown inline option flag");
      }
    }
  }

  @Override
  public void visitRegExpCharRange(RegExpCharRange range) {
    final RegExpChar from = range.getFrom();
    final RegExpChar to = range.getTo();
    if (to != null) {
      checkRange(range, from.getValue(), to.getValue());
    }
  }

  private void checkRange(RegExpCharRange range, int fromCodePoint, int toCodePoint) {
    if (fromCodePoint == -1 || toCodePoint == -1) {
      return;
    }
    int errorStart = range.getTextOffset();
    int errorEnd = errorStart + range.getTextLength();
    // \ud800\udc00-\udbff\udfff
    if (!Character.isSupplementaryCodePoint(fromCodePoint) && Character.isLowSurrogate((char)fromCodePoint)) {
      final PsiElement prevSibling = range.getPrevSibling();
      if (prevSibling instanceof RegExpChar) {
        final int prevSiblingValue = ((RegExpChar)prevSibling).getValue();
        if (!Character.isSupplementaryCodePoint(prevSiblingValue) && Character.isHighSurrogate((char)prevSiblingValue)) {
          fromCodePoint = Character.toCodePoint((char)prevSiblingValue, (char)fromCodePoint);
          errorStart -= prevSibling.getTextLength();
        }
      }
    }
    if (!Character.isSupplementaryCodePoint(toCodePoint) && Character.isHighSurrogate((char)toCodePoint)) {
      final PsiElement nextSibling = range.getNextSibling();
      if (nextSibling instanceof RegExpChar) {
        final int nextSiblingValue = ((RegExpChar)nextSibling).getValue();
        if (!Character.isSupplementaryCodePoint(nextSiblingValue) && Character.isLowSurrogate((char)nextSiblingValue)) {
          toCodePoint = Character.toCodePoint((char)toCodePoint, (char)nextSiblingValue);
          errorEnd += nextSibling.getTextLength();
        }
      }
    }
    if (toCodePoint < fromCodePoint) {
      myHolder.createErrorAnnotation(new TextRange(errorStart, errorEnd), "Illegal character range (to < from)");
    }
    else if (toCodePoint == fromCodePoint) {
      myHolder.createWarningAnnotation(new TextRange(errorStart, errorEnd), "Redundant character range");
    }
  }

  @Override
  public void visitRegExpBoundary(RegExpBoundary boundary) {
    if (!myLanguageHosts.supportsBoundary(boundary)) {
      myHolder.createErrorAnnotation(boundary, "This boundary is not supported in this regex dialect");
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
      final int value = regExpChar.getValue();
      if (value != -1 && !seen.add(value)) {
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
  }

  @Override
  public void visitRegExpChar(final RegExpChar ch) {
    final PsiElement child = ch.getFirstChild();
    final IElementType type = child.getNode().getElementType();
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
    final RegExpChar.Type charType = ch.getType();
    if (charType == RegExpChar.Type.HEX || charType == RegExpChar.Type.UNICODE) {
      if (ch.getValue() == -1) {
        myHolder.createErrorAnnotation(ch, "Illegal unicode escape sequence");
        return;
      }
      if (text.charAt(text.length() - 1) == '}') {
        if (!myLanguageHosts.supportsExtendedHexCharacter(ch)) {
          myHolder.createErrorAnnotation(ch, "This hex character syntax is not supported in this regex dialect");
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
      myHolder.createErrorAnnotation(namedCharacter, "Named Unicode characters are not allowed in this regex dialect");
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
          final RegExpGroup.Type type = group.getType();
          if (type == RegExpGroup.Type.CAPTURING_GROUP || type == RegExpGroup.Type.ATOMIC || type == RegExpGroup.Type.NON_CAPTURING) {
            final RegExpGroup innerGroup = (RegExpGroup)atoms[0];
            if (group.isCapturing() == innerGroup.isCapturing()) {
              myHolder.createWarningAnnotation(group, "Redundant group nesting");
            }
          }
        }
      }
    }
    if (group.isAnyNamedGroup()) {
      if (!myLanguageHosts.supportsNamedGroupSyntax(group)) {
        myHolder.createErrorAnnotation(group, "This named group syntax is not supported in this regex dialect");
      }
    }
    if (group.getType() == RegExpGroup.Type.ATOMIC && !myLanguageHosts.supportsPossessiveQuantifiers(group)) {
      myHolder.createErrorAnnotation(group, "Atomic groups are not supported in this regex dialect");
    }
    final String name = group.getName();
    if (name != null && !myLanguageHosts.isValidGroupName(name, group)) {
      final ASTNode node = group.getNode().findChildByType(RegExpTT.NAME);
      if (node != null) myHolder.createErrorAnnotation(node, "Invalid group name");
    }
    final AnnotationSession session = myHolder.getCurrentAnnotationSession();
    final Map<String, RegExpGroup> namedGroups = NAMED_GROUP_MAP.get(session, new HashMap<>());
    if (namedGroups.isEmpty()) session.putUserData(NAMED_GROUP_MAP, namedGroups);
    if (namedGroups.put(name, group) != null) {
      final ASTNode node = group.getNode().findChildByType(RegExpTT.NAME);
      if (node != null) myHolder.createErrorAnnotation(node, "Group with name '" + name + "' already defined");
    }
    final RegExpGroup.Type groupType = group.getType();
    if (groupType == RegExpGroup.Type.POSITIVE_LOOKBEHIND || groupType == RegExpGroup.Type.NEGATIVE_LOOKBEHIND) {
      final RegExpLanguageHost.Lookbehind support = myLanguageHosts.supportsLookbehind(group);
      if (support == RegExpLanguageHost.Lookbehind.NOT_SUPPORTED) {
        myHolder.createErrorAnnotation(group, "Look-behind groups are not supported in this regex dialect");
      }
      else {
        group.accept(new LookbehindVisitor(support, myHolder));
      }
    }
  }

  @Override
  public void visitRegExpNamedGroupRef(RegExpNamedGroupRef groupRef) {
    if (!myLanguageHosts.supportsNamedGroupRefSyntax(groupRef)) {
      myHolder.createErrorAnnotation(groupRef, "This named group reference syntax is not supported in this regex dialect");
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
        myHolder.createErrorAnnotation(comment, "Embedded comments are not supported in this regex dialect");
      }
    }
  }

  @Override
  public void visitRegExpPyCondRef(RegExpPyCondRef condRef) {
    if (!myLanguageHosts.supportsPythonConditionalRefs(condRef)) {
      myHolder.createErrorAnnotation(condRef, "Conditional references are not supported in this regex dialect");
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
  public void visitRegExpClosure(RegExpClosure closure) {
    if (closure.getAtom() instanceof RegExpSetOptions) {
      myHolder.createErrorAnnotation(closure.getQuantifier(), "Dangling metacharacter");
    }
  }

  @Override
  public void visitRegExpQuantifier(RegExpQuantifier quantifier) {
    if (quantifier.isCounted()) {
      final RegExpNumber minElement = quantifier.getMin();
      final String min = minElement == null ? "" : minElement.getText();
      final RegExpNumber maxElement = quantifier.getMax();
      final String max = maxElement == null ? "" : maxElement.getText();
      if (!max.isEmpty() && max.equals(min)) {
        if ("1".equals(max)) {
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
      else if (("0".equals(min) || min.isEmpty()) && "1".equals(max)) {
        final Annotation a = myHolder.createWeakWarningAnnotation(quantifier, "Repetition range replaceable by '?'");
        registerFix(a, new SimplifyQuantifierAction(quantifier, "?"));
      }
      else if (("0".equals(min) || min.isEmpty()) && max.isEmpty()) {
        final Annotation a = myHolder.createWeakWarningAnnotation(quantifier, "Repetition range replaceable by '*'");
        registerFix(a, new SimplifyQuantifierAction(quantifier, "*"));
      }
      else if ("1".equals(min) && max.isEmpty()) {
        final Annotation a = myHolder.createWeakWarningAnnotation(quantifier, "Repetition range replaceable by '+'");
        registerFix(a, new SimplifyQuantifierAction(quantifier, "+"));
      }
      Number minValue = null;
      if (minElement != null) {
        minValue = myLanguageHosts.getQuantifierValue(minElement);
        if (minValue == null) myHolder.createErrorAnnotation(minElement, "Repetition value too large");
      }
      Number maxValue = null;
      if (maxElement != null) {
        maxValue= myLanguageHosts.getQuantifierValue(maxElement);
        if (maxValue == null) myHolder.createErrorAnnotation(maxElement, "Repetition value too large");
      }
      if (minValue != null && maxValue != null) {
        if (minValue.longValue() > maxValue.longValue() || minValue.doubleValue() > maxValue.doubleValue()) {
          final TextRange range = new TextRange(minElement.getTextOffset(), maxElement.getTextOffset() + maxElement.getTextLength());
          myHolder.createErrorAnnotation(range, "Illegal repetition range (min > max)");
        }
      }
    }
    if (quantifier.isPossessive() && !myLanguageHosts.supportsPossessiveQuantifiers(quantifier)) {
      final ASTNode modifier = quantifier.getModifier();
      assert modifier != null;
      myHolder.createErrorAnnotation(modifier, "Nested quantifier in regexp");
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


  private static class LookbehindVisitor extends RegExpRecursiveElementVisitor {

    private final RegExpLanguageHost.Lookbehind mySupport;
    private final AnnotationHolder myHolder;
    private int myLength = 0;
    private boolean myStop = false;

    LookbehindVisitor(RegExpLanguageHost.Lookbehind support, AnnotationHolder holder) {
      mySupport = support;
      myHolder = holder;
    }

    @Override
    public void visitRegExpElement(RegExpElement element) {
      if (myStop) {
        return;
      }
      super.visitRegExpElement(element);
    }

    @Override
    public void visitRegExpChar(RegExpChar ch) {
      super.visitRegExpChar(ch);
      myLength++;
    }

    @Override
    public void visitSimpleClass(RegExpSimpleClass simpleClass) {
      super.visitSimpleClass(simpleClass);
      myLength++;
    }

    @Override
    public void visitRegExpClass(RegExpClass expClass) {
      super.visitRegExpClass(expClass);
      myLength++;
    }

    @Override
    public void visitRegExpProperty(RegExpProperty property) {
      super.visitRegExpProperty(property);
      myLength++;
    }

    @Override
    public void visitRegExpBackref(RegExpBackref backref) {
      super.visitRegExpBackref(backref);
      if (mySupport != RegExpLanguageHost.Lookbehind.FULL) {
        stopAndReportError(backref, "Group reference not allowed inside lookbehind");
      }
    }

    @Override
    public void visitRegExpPattern(RegExpPattern pattern) {
      if (mySupport != RegExpLanguageHost.Lookbehind.FIXED_LENGTH_ALTERNATION) {
        super.visitRegExpPattern(pattern);
        return;
      }
      final int length = myLength;
      int branchLength = -1;
      final RegExpBranch[] branches = pattern.getBranches();
      for (RegExpBranch branch : branches) {
        myLength = 0;
        super.visitRegExpBranch(branch);
        if (branchLength == -1) {
          branchLength = myLength;
        } else if (branchLength != myLength) {
          stopAndReportError(pattern, "Alternation alternatives needs to have the same length inside lookbehind");
          return;
        }
      }
      myLength = length;
    }

    @Override
    public void visitRegExpQuantifier(RegExpQuantifier quantifier) {
      super.visitRegExpQuantifier(quantifier);
      if (mySupport == RegExpLanguageHost.Lookbehind.FULL) {
        return;
      }
      if (quantifier.isCounted()) {
        final RegExpNumber minElement = quantifier.getMin();
        final RegExpNumber maxElement = quantifier.getMax();
        if (minElement != null && maxElement != null) {
          try {
            final long min = Long.parseLong(minElement.getText());
            final long max = Long.parseLong(maxElement.getText());
            if (min == max) {
              myLength += min;
              return;
            }
          } catch (NumberFormatException ignore) {}
        }
        if (mySupport != RegExpLanguageHost.Lookbehind.FINITE_REPETITION) {
          stopAndReportError(quantifier, "Unequal min and max in counted quantifier not allowed inside lookbehind");
        }
      }
      else {
        final ASTNode token = quantifier.getToken();
        assert token != null;
        final String tokenText = token.getText();
        if ("?".equals(tokenText) && mySupport == RegExpLanguageHost.Lookbehind.FINITE_REPETITION) {
          return;
        }
        stopAndReportError(quantifier, tokenText + " repetition not allowed inside lookbehind");
      }
    }

    @Override
    public void visitRegExpBoundary(RegExpBoundary boundary) {
      super.visitRegExpBoundary(boundary);
      // is zero length
    }

    @Override
    public void visitRegExpNamedGroupRef(RegExpNamedGroupRef groupRef) {
      super.visitRegExpNamedGroupRef(groupRef);
      if (mySupport != RegExpLanguageHost.Lookbehind.FULL) {
        stopAndReportError(groupRef, "Named group reference not allowed inside lookbehind");
      }
    }

    @Override
    public void visitRegExpPyCondRef(RegExpPyCondRef condRef) {
      super.visitRegExpPyCondRef(condRef);
      if (mySupport != RegExpLanguageHost.Lookbehind.FULL) {
        stopAndReportError(condRef, "Conditional group reference not allowed inside lookbehind");
      }
    }

    @Override
    public void visitPosixBracketExpression(RegExpPosixBracketExpression posixBracketExpression) {
      super.visitPosixBracketExpression(posixBracketExpression);
      myLength++;
    }

    public void stopAndReportError(RegExpElement element, String message) {
      myHolder.createErrorAnnotation(element, message);
      myStop = true;
    }
  }
}
