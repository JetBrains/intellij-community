/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions.moveUpDown;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClassLevelDeclarationStatement;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class DeclarationMover extends LineMover {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.actions.moveUpDown.DeclarationMover");
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
      if (c1 == c2 || c1 != ',' && c2 != ',') {
        return;
      }
      if (c1 == ';' || c2 == ';') {
        document.replaceString(end1, end1 + 1, String.valueOf(c2));
        document.replaceString(end2, end2 + 1, String.valueOf(c1));
      }
      else if (c1 == ',') {
        document.deleteString(end1, end1 + 1);
        document.insertString(end2 + 1, ",");
      }
      else {
        document.deleteString(end2, end2 + 1);
        document.insertString(end1 + 1, ",");
      }
    }
  }

  @Override
  public boolean checkAvailable(@NotNull final Editor editor, @NotNull final PsiFile file, @NotNull final MoveInfo info, final boolean down) {
    if (!(file instanceof PsiJavaFile)) {
      return false;
    }

    boolean available = super.checkAvailable(editor, file, info, down);
    if (!available) return false;

    LineRange oldRange = info.toMove;
    final Pair<PsiElement, PsiElement> psiRange = getElementRange(editor, file, oldRange);
    if (psiRange == null) return false;

    final PsiMember firstMember = PsiTreeUtil.getParentOfType(psiRange.getFirst(), PsiMember.class, false);
    PsiElement endElement = psiRange.getSecond();
    if (firstMember instanceof PsiEnumConstant && endElement instanceof PsiJavaToken) {
      final IElementType tokenType = ((PsiJavaToken)endElement).getTokenType();
      if (down && tokenType == JavaTokenType.SEMICOLON) {
        return info.prohibitMove();
      }
      if (tokenType == JavaTokenType.COMMA || tokenType == JavaTokenType.SEMICOLON) {
        endElement = PsiTreeUtil.skipSiblingsBackward(endElement, PsiWhiteSpace.class);
      }
    }
    final PsiMember lastMember = PsiTreeUtil.getParentOfType(endElement, PsiMember.class, false);
    if (firstMember == null || lastMember == null) return false;

    LineRange range;
    if (firstMember == lastMember) {
      moveEnumConstant = firstMember instanceof PsiEnumConstant;
      range = memberRange(firstMember, editor, oldRange);
      if (range == null) return false;
      range.firstElement = range.lastElement = firstMember;
    }
    else {
      final PsiElement parent = PsiTreeUtil.findCommonParent(firstMember, lastMember);
      if (parent == null) return false;

      final Pair<PsiElement, PsiElement> combinedRange = getElementRange(parent, firstMember, lastMember);
      if (combinedRange == null) return false;
      final LineRange lineRange1 = memberRange(combinedRange.getFirst(), editor, oldRange);
      if (lineRange1 == null) return false;
      final LineRange lineRange2 = memberRange(combinedRange.getSecond(), editor, oldRange);
      if (lineRange2 == null) return false;
      range = new LineRange(lineRange1.startLine, lineRange2.endLine);
      range.firstElement = combinedRange.getFirst();
      range.lastElement = combinedRange.getSecond();
    }
    Document document = editor.getDocument();

    PsiElement sibling = down ? range.lastElement.getNextSibling() : range.firstElement.getPrevSibling();
    sibling = firstNonWhiteElement(sibling, down);
    if (range.lastElement instanceof PsiEnumConstant && sibling instanceof PsiJavaToken) {
      final PsiJavaToken token = (PsiJavaToken)sibling;
      final IElementType tokenType = token.getTokenType();
      if (down && tokenType == JavaTokenType.SEMICOLON) {
        return info.prohibitMove();
      }
      if (tokenType == JavaTokenType.COMMA) {
        sibling = down ?
                  PsiTreeUtil.skipSiblingsForward(sibling, PsiWhiteSpace.class) :
                  PsiTreeUtil.skipSiblingsBackward(sibling, PsiWhiteSpace.class);
      }
    }
    final boolean areWeMovingClass = range.firstElement instanceof PsiClass;
    info.toMove = range;
    try {
      LineRange intraClassRange = moveInsideOutsideClassPosition(editor, sibling, down, areWeMovingClass);
      if (intraClassRange == null) {
        info.toMove2 = new LineRange(sibling, sibling, document);
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
      info.toMove2 = null;
    }
    return true;
  }

  private static LineRange memberRange(@NotNull PsiElement member, Editor editor, LineRange lineRange) {
    final TextRange textRange = member.getTextRange();
    if (editor.getDocument().getTextLength() < textRange.getEndOffset()) return null;
    final int startLine = editor.offsetToLogicalPosition(textRange.getStartOffset()).line;
    final int endLine = editor.offsetToLogicalPosition(textRange.getEndOffset()).line+1;
    if (!isInsideDeclaration(member, startLine, endLine, lineRange, editor)) return null;

    return new LineRange(startLine, endLine);
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
    if (member instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)member;
      if (aClass instanceof PsiAnonymousClass) return false; // move new expression instead of anon class
      PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
      if (nameIdentifier != null) memberSuspects.add(nameIdentifier);
    }
    if (member instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)member;
      PsiIdentifier nameIdentifier = method.getNameIdentifier();
      if (nameIdentifier != null) memberSuspects.add(nameIdentifier);
      PsiTypeElement returnTypeElement = method.getReturnTypeElement();
      if (returnTypeElement != null) memberSuspects.add(returnTypeElement);
    }
    if (member instanceof PsiField) {
      final PsiField field = (PsiField)member;
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
    if (sibling instanceof PsiJavaToken &&
        ((PsiJavaToken)sibling).getTokenType() == (isDown ? JavaTokenType.RBRACE : JavaTokenType.LBRACE) &&
        sibling.getParent() instanceof PsiClass) {
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
        && (sibling instanceof PsiJavaToken && ((PsiJavaToken)sibling).getTokenType() == JavaTokenType.SEMICOLON || sibling instanceof PsiErrorElement)
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
        if (!(anchor instanceof PsiJavaToken && ((PsiJavaToken)anchor).getTokenType() == JavaTokenType.SEMICOLON)) {
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
