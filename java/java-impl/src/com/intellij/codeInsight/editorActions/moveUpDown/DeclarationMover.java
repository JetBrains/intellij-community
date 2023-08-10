// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.moveUpDown;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.jsp.jspJava.JspClassLevelDeclarationStatement;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class DeclarationMover extends LineMover {
  private static final Logger LOG = Logger.getInstance(DeclarationMover.class);
  @SuppressWarnings("StatefulEp")
  private PsiEnumConstant myEnumToInsertSemicolonAfter;
  private boolean moveEnumConstant;

  @Override
  public void beforeMove(@NotNull final Editor editor, @NotNull final MoveInfo info, final boolean down) {
    super.beforeMove(editor, info, down);

    if (myEnumToInsertSemicolonAfter != null) {
      TreeElement semicolon = Factory.createSingleLeafElement(JavaTokenType.SEMICOLON, ";", 0, 1, null, myEnumToInsertSemicolonAfter.getManager());

      try {
        PsiElement inserted = myEnumToInsertSemicolonAfter.getParent().addAfter(semicolon.getPsi(), myEnumToInsertSemicolonAfter);
        inserted = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(inserted);
        final LogicalPosition position = editor.offsetToLogicalPosition(inserted.getTextRange().getEndOffset());

        info.toMove2 = new LineRange(position.line + 1, position.line + 1);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
      finally {
        myEnumToInsertSemicolonAfter = null;
      }
    }
  }

  @Override
  public void afterMove(@NotNull Editor editor, @NotNull PsiFile file, @NotNull MoveInfo info, boolean down) {
    super.afterMove(editor, file, info, down);
    if (moveEnumConstant) {
      final Document document = editor.getDocument();
      final CharSequence cs = document.getCharsSequence();
      int end1 = info.range1.getEndOffset();
      char c1 = cs.charAt(--end1);
      while (Character.isWhitespace(c1)) {
        c1 = cs.charAt(--end1);
      }
      int end2 = info.range2.getEndOffset();
      char c2 = cs.charAt(--end2);
      while (Character.isWhitespace(c2)) {
        c2 = cs.charAt(--end2);
      }
      if (c1 == c2 || !contains(info.range1, end1) || !contains(info.range2, end2)) {
        return;
      }
      if (c1 == ',' || c1 == ';') {
        document.deleteString(end1, end1 + 1);
        if (end1 < end2) {
          end1--;
          end2--;
        }
        document.insertString(end2 + 1, String.valueOf(c1));
      }
      if (c2 == ',' || c2 == ';'){
        document.deleteString(end2, end2 + 1);
        if (end2 < end1) end1--;
        document.insertString(end1 + 1, String.valueOf(c2));
      }
    }
  }

  private static boolean contains(RangeMarker rangeMarker, int index) {
    return rangeMarker.getStartOffset() <= index && rangeMarker.getEndOffset() >= index;
  }

  @Override
  public boolean checkAvailable(@NotNull final Editor editor, @NotNull final PsiFile file, @NotNull final MoveInfo info, final boolean down) {
    if (!(file instanceof PsiJavaFile)) {
      return false;
    }

    boolean available = super.checkAvailable(editor, file, info, down);
    if (!available) return false;

    final Pair<PsiElement, PsiElement> psiRange = getElementRange(editor, file, info.toMove);
    if (psiRange == null) return false;

    final PsiMember firstMember = PsiTreeUtil.getParentOfType(psiRange.getFirst(), PsiMember.class, false);
    PsiElement endElement = psiRange.getSecond();
    if (firstMember instanceof PsiEnumConstant && endElement instanceof PsiJavaToken) {
      final IElementType tokenType = ((PsiJavaToken)endElement).getTokenType();
      if (down && tokenType == JavaTokenType.SEMICOLON) {
        return info.prohibitMove();
      }
      if (tokenType == JavaTokenType.COMMA || tokenType == JavaTokenType.SEMICOLON) {
        endElement = PsiTreeUtil.skipWhitespacesBackward(endElement);
      }
    }
    PsiMember lastMember = PsiTreeUtil.getParentOfType(endElement, PsiMember.class, false);
    if (firstMember == null || lastMember == null) return false;
    if (lastMember instanceof PsiEnumConstantInitializer enumConstantInitializer) {
      lastMember = enumConstantInitializer.getEnumConstant();
    }

    LineRange range;
    if (firstMember == lastMember) {
      moveEnumConstant = firstMember instanceof PsiEnumConstant;
      range = memberRange(firstMember, editor, info.toMove);
      if (range == null) return false;
      range.firstElement = range.lastElement = firstMember;
    }
    else {
      final PsiElement parent = PsiTreeUtil.findCommonParent(firstMember, lastMember);
      if (parent == null) return false;

      final Pair<PsiElement, PsiElement> combinedRange = getElementRange(parent, firstMember, lastMember);
      if (combinedRange == null) return false;
      final LineRange lineRange1 = memberRange(combinedRange.getFirst(), editor, info.toMove);
      if (lineRange1 == null) return false;
      final LineRange lineRange2 = memberRange(combinedRange.getSecond(), editor, info.toMove);
      if (lineRange2 == null) return false;
      range = new LineRange(lineRange1.startLine, lineRange2.endLine);
      range.firstElement = combinedRange.getFirst();
      range.lastElement = combinedRange.getSecond();
    }
    Document document = editor.getDocument();

    PsiElement sibling = (down ? range.endLine >= document.getLineCount() : range.startLine == 0) ? null :
                         firstNonWhiteElement(down ? document.getLineStartOffset(range.endLine)
                                                   : document.getLineEndOffset(range.startLine - 1),
                                              file, down);
    if (range.lastElement instanceof PsiEnumConstant) {
      if (sibling instanceof PsiJavaToken token) {
        final IElementType tokenType = token.getTokenType();
        if (down && tokenType == JavaTokenType.SEMICOLON) {
          return info.prohibitMove();
        }
        if (tokenType == JavaTokenType.COMMA) {
          sibling = down ?
                    PsiTreeUtil.skipWhitespacesForward(sibling) :
                    PsiTreeUtil.skipWhitespacesBackward(sibling);
        }
      }
      else if (sibling instanceof PsiField && !(sibling instanceof PsiEnumConstant)) {
        // do not move enum constant past regular field
        return info.prohibitMove();
      }
    }
    final boolean areWeMovingClass = range.firstElement instanceof PsiClass;
    info.toMove = range;

    int neighbourLine = down ? range.endLine : range.startLine - 1;
    if (neighbourLine >= 0 && neighbourLine < document.getLineCount() &&
        CharArrayUtil.containsOnlyWhiteSpaces(document.getImmutableCharSequence().subSequence(document.getLineStartOffset(neighbourLine),
                                                                                              document.getLineEndOffset(neighbourLine))) &&
      emptyLineCanBeDeletedAccordingToCodeStyle(file, document, document.getLineEndOffset(neighbourLine))) {
      info.toMove2 = new LineRange(neighbourLine, neighbourLine + 1);
    }
    else {
      try {
        LineRange intraClassRange = moveInsideOutsideClassPosition(editor, sibling, down, areWeMovingClass);
        if (intraClassRange == null) {
          Couple<LineRange> splitRange = extractCommentRange(sibling);
          info.toMove2 = splitRange.first.startLine == splitRange.first.endLine || !down ? splitRange.second : splitRange.first;
          if (down && sibling.getNextSibling() == null) return false;
        }
        else {
          info.toMove2 = intraClassRange;
        }
        if (down ? info.toMove2.startLine < info.toMove.endLine : info.toMove2.endLine > info.toMove.startLine) {
          return false;
        }
      }
      catch (IllegalMoveException e) {
        info.prohibitMove();
      }
    }
    return true;
  }

  private static boolean emptyLineCanBeDeletedAccordingToCodeStyle(PsiFile file, Document document, int offset) {
    CharSequence text = document.getImmutableCharSequence();
    String whitespace = " \t\n";
    int whitespaceStartOffset = CharArrayUtil.shiftBackward(text, offset - 1, whitespace) + 1;
    int whitespaceEndOffset = CharArrayUtil.shiftForward(text, offset, whitespace);
    int minLineFeeds = CodeStyleManager.getInstance(file.getProject()).getMinLineFeeds(file, whitespaceEndOffset);
    int actualLineFeeds = StringUtil.countNewLines(text.subSequence(whitespaceStartOffset, whitespaceEndOffset));
    return actualLineFeeds > minLineFeeds;
  }

  private static LineRange memberRange(@NotNull PsiElement member, Editor editor, LineRange lineRange) {
    final TextRange textRange = member.getTextRange();
    if (editor.getDocument().getTextLength() < textRange.getEndOffset()) return null;
    int startLine = editor.offsetToLogicalPosition(textRange.getStartOffset()).line;
    int endLine = editor.offsetToLogicalPosition(textRange.getEndOffset()).line+1;

    // if member includes a comment (non-javadoc) and it wasn't selected by user, don't move it with member
    Couple<LineRange> splitRanges = extractCommentRange(member);
    if (lineRange.startLine >= splitRanges.first.endLine) startLine = splitRanges.second.startLine;
    else if (lineRange.endLine < splitRanges.second.startLine) endLine = splitRanges.first.endLine;

    if (!isInsideDeclaration(member, startLine, endLine, lineRange, editor)) return null;

    return new LineRange(startLine, endLine);
  }

  private static Couple<LineRange> extractCommentRange(@NotNull PsiElement member) {
    PsiElement firstChild = member.getFirstChild();
    PsiElement firstCoreChild = firstChild;
    while (firstCoreChild instanceof PsiComment && !(firstCoreChild instanceof PsiDocComment) || firstCoreChild instanceof PsiWhiteSpace) {
      firstCoreChild = firstCoreChild.getNextSibling();
    }
    PsiElement lastAttachedChild = PsiTreeUtil.skipWhitespacesBackward(firstCoreChild);
    if (lastAttachedChild == null) {
      LineRange wholeRange = new LineRange(member);
      return Couple.of(new LineRange(wholeRange.startLine, wholeRange.startLine), wholeRange);
    }
    else {
      return Couple.of(new LineRange(firstChild, lastAttachedChild), new LineRange(firstCoreChild, member));
    }
  }

  private static boolean isInsideDeclaration(@NotNull final PsiElement member,
                                             final int startLine,
                                             final int endLine,
                                             final LineRange lineRange,
                                             final Editor editor) {
    // if we positioned on member start or end we'll be able to move it
    if (startLine == lineRange.startLine || startLine == lineRange.endLine || endLine == lineRange.startLine ||
        endLine == lineRange.endLine) {
      return true;
    }
    List<PsiElement> memberSuspects = new ArrayList<>();
    PsiModifierList modifierList = member instanceof PsiMember ? ((PsiMember)member).getModifierList() : null;
    if (modifierList != null) memberSuspects.add(modifierList);
    if (member instanceof PsiClass aClass) {
      if (aClass instanceof PsiAnonymousClass) return false; // move new expression instead of anon class
      PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
      if (nameIdentifier != null) memberSuspects.add(nameIdentifier);
    }
    if (member instanceof PsiMethod method) {
      PsiIdentifier nameIdentifier = method.getNameIdentifier();
      if (nameIdentifier != null) memberSuspects.add(nameIdentifier);
      PsiTypeElement returnTypeElement = method.getReturnTypeElement();
      if (returnTypeElement != null) memberSuspects.add(returnTypeElement);
    }
    if (member instanceof PsiField field) {
      PsiIdentifier nameIdentifier = field.getNameIdentifier();
      memberSuspects.add(nameIdentifier);
      PsiTypeElement typeElement = field.getTypeElement();
      if (typeElement != null) memberSuspects.add(typeElement);
    }
    TextRange lineTextRange = new TextRange(editor.getDocument().getLineStartOffset(lineRange.startLine), editor.getDocument().getLineEndOffset(lineRange.endLine));
    for (PsiElement suspect : memberSuspects) {
      TextRange textRange = suspect.getTextRange();
      if (textRange != null && lineTextRange.intersects(textRange)) return true;
    }
    return false;
  }

  private static class IllegalMoveException extends Exception {
  }

  // null means we are not crossing class border
  // throws IllegalMoveException when corresponding movement has no sense
  @Nullable
  private LineRange moveInsideOutsideClassPosition(Editor editor, PsiElement sibling, final boolean isDown, boolean areWeMovingClass) throws IllegalMoveException{
    if (sibling == null || sibling instanceof PsiImportList) throw new IllegalMoveException();
    if (PsiUtil.isJavaToken(sibling, (isDown ? JavaTokenType.RBRACE : JavaTokenType.LBRACE)) && sibling.getParent() instanceof PsiClass) {
      // moving outside class
      final PsiClass aClass = (PsiClass)sibling.getParent();
      final PsiElement parent = aClass.getParent();
      if (!areWeMovingClass && !(parent instanceof PsiClass)) throw new IllegalMoveException();
      if (aClass instanceof PsiAnonymousClass) throw new IllegalMoveException();
      PsiElement start = isDown ? sibling : aClass.getModifierList();
      return new LineRange(start, sibling, editor.getDocument());
      //return isDown ? nextLineOffset(editor, aClass.getTextRange().getEndOffset()) : aClass.getTextRange().getStartOffset();
    }
    // trying to move up inside enum constant list, move outside of enum class instead
    if (!isDown
        && sibling.getParent() instanceof PsiClass
        && (PsiUtil.isJavaToken(sibling, JavaTokenType.SEMICOLON) || sibling instanceof PsiErrorElement)
        && firstNonWhiteElement(sibling.getPrevSibling(), false) instanceof PsiEnumConstant) {
      PsiClass aClass = (PsiClass)sibling.getParent();
      if (!areWeMovingClass && !(aClass.getParent() instanceof PsiClass)) throw new IllegalMoveException();
      Document document = editor.getDocument();
      int startLine = document.getLineNumber(aClass.getTextRange().getStartOffset());
      int endLine = document.getLineNumber(sibling.getTextRange().getEndOffset()) + 1;
      return new LineRange(startLine, endLine);
    }
    if (sibling instanceof PsiClass) {
      // moving inside class
      PsiClass aClass = (PsiClass)sibling;
      if (aClass instanceof PsiAnonymousClass) throw new IllegalMoveException();
      if (isDown) {
        PsiElement child = aClass.getFirstChild();
        if (child == null) throw new IllegalMoveException();
        return new LineRange(child, aClass.isEnum() ? afterEnumConstantsPosition(aClass) : aClass.getLBrace(),
                             editor.getDocument());
      }
      else {
        PsiElement rBrace = aClass.getRBrace();
        if (rBrace == null) throw new IllegalMoveException();
        return new LineRange(rBrace, rBrace, editor.getDocument());
      }
    }
    if (sibling instanceof JspClassLevelDeclarationStatement) {
      // there should be another scriptlet/decl to move
      if (firstNonWhiteElement(isDown ? sibling.getNextSibling() : sibling.getPrevSibling(), isDown) == null) throw new IllegalMoveException();
    }
    return null;
  }

  private PsiElement afterEnumConstantsPosition(final PsiClass aClass) {
    PsiField[] fields = aClass.getFields();
    for (int i = fields.length-1;i>=0; i--) {
      PsiField field = fields[i];
      if (field instanceof PsiEnumConstant) {
        PsiElement anchor = firstNonWhiteElement(field.getNextSibling(), true);
        if (!(PsiUtil.isJavaToken(anchor, JavaTokenType.SEMICOLON))) {
          anchor = field;
          myEnumToInsertSemicolonAfter = (PsiEnumConstant)field;
        }
        return anchor;
      }
    }
    // no enum constants at all ?
    return aClass.getLBrace();
  }
}
