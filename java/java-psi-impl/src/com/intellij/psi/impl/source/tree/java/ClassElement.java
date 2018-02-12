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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClassElement extends CompositeElement implements Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.ClassElement");

  private static final TokenSet MODIFIERS_TO_REMOVE_IN_INTERFACE_BIT_SET = TokenSet.create(
    PUBLIC_KEYWORD, ABSTRACT_KEYWORD, STATIC_KEYWORD, FINAL_KEYWORD, NATIVE_KEYWORD);
  private static final TokenSet MODIFIERS_TO_REMOVE_IN_INTERFACE_BIT_SET_18_METHOD = TokenSet.create(
    PUBLIC_KEYWORD, ABSTRACT_KEYWORD, FINAL_KEYWORD, NATIVE_KEYWORD);
  private static final TokenSet MODIFIERS_TO_REMOVE_IN_ENUM_BIT_SET = TokenSet.create(
    PUBLIC_KEYWORD, FINAL_KEYWORD);
  private static final TokenSet ENUM_CONSTANT_LIST_ELEMENTS_BIT_SET = TokenSet.create(
    ENUM_CONSTANT, COMMA, SEMICOLON);

  public ClassElement(IElementType type) {
    super(type);
  }

  @Override
  public int getTextOffset() {
    ASTNode name = findChildByRole(ChildRole.NAME);
    if (name != null) {
      return name.getStartOffset();
    }
    else {
      return super.getTextOffset();
    }
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    PsiClass psiClass = (PsiClass)SourceTreeToPsiMap.treeElementToPsi(this);
    if (anchor == null) {
      if (first.getElementType() != JavaDocElementType.DOC_COMMENT) {
        if (before == null) {
          if (first == last) {
            PsiElement firstPsi = SourceTreeToPsiMap.treeElementToPsi(first);
            if (firstPsi instanceof PsiEnumConstant) {
              anchor = findEnumConstantListDelimiterPlace();
              before = anchor != findChildByRole(ChildRole.LBRACE);
            }
            else {
              PsiElement psiElement = firstPsi instanceof PsiMember
                                      ? JavaPsiImplementationHelper.getInstance(psiClass.getProject()).getDefaultMemberAnchor(psiClass, (PsiMember)firstPsi)
                                      : null;
              anchor = psiElement != null ? SourceTreeToPsiMap.psiElementToTree(psiElement) : null;
              before = Boolean.TRUE;
            }
          }
          else {
            anchor = findChildByRole(ChildRole.RBRACE);
            before = Boolean.TRUE;
          }
        }
        else if (!before.booleanValue()) {
          anchor = findChildByRole(ChildRole.LBRACE);
        }
        else {
          anchor = findChildByRole(ChildRole.RBRACE);
        }
      }
    }

    if (isEnum()) {
      if (!ENUM_CONSTANT_LIST_ELEMENTS_BIT_SET.contains(first.getElementType())) {
        ASTNode semicolonPlace = findEnumConstantListDelimiterPlace();
        boolean commentsOrWhiteSpaces = true;
        for (ASTNode child = first; child != null; child = child.getTreeNext()) {
          if (!PsiImplUtil.isWhitespaceOrComment(child)) {
            commentsOrWhiteSpaces = false;
            break;
          }
        }
        if (!commentsOrWhiteSpaces && (semicolonPlace == null || semicolonPlace.getElementType() != SEMICOLON)) {
            final LeafElement semicolon = Factory.createSingleLeafElement(SEMICOLON, ";", 0, 1,
                                                                          SharedImplUtil.findCharTableByTree(this), getManager());
            addInternal(semicolon, semicolon, semicolonPlace, Boolean.FALSE);
            semicolonPlace = semicolon;
        }
        for (ASTNode run = anchor; run != null; run = run.getTreeNext()) {
          if (run == semicolonPlace) {
            anchor = before.booleanValue() ? semicolonPlace.getTreeNext() : semicolonPlace;
            if (anchor != null && PsiImplUtil.isWhitespaceOrComment(anchor)) {
              anchor = PsiTreeUtil.skipWhitespacesAndCommentsForward(anchor.getPsi()).getNode();
            }
            break;
          }
        }
      }
    }

    ASTNode afterLast = last.getTreeNext();
    ASTNode next;
    for (ASTNode child = first; child != afterLast; child = next) {
      next = child.getTreeNext();
      if (child.getElementType() == JavaElementType.METHOD && ((PsiMethod)SourceTreeToPsiMap.treeElementToPsi(child)).isConstructor()) {
        ASTNode oldIdentifier = ((CompositeElement)child).findChildByRole(ChildRole.NAME);
        ASTNode newIdentifier = findChildByRole(ChildRole.NAME).copyElement();
        newIdentifier.putUserData(CharTable.CHAR_TABLE_KEY, SharedImplUtil.findCharTableByTree(this));
        child.replaceChild(oldIdentifier, newIdentifier);
      }
    }

    if (psiClass.isEnum()) {
      for (ASTNode child = first; child != afterLast; child = next) {
        next = child.getTreeNext();
        if ((child.getElementType() == JavaElementType.METHOD && ((PsiMethod)SourceTreeToPsiMap.treeElementToPsi(child)).isConstructor()) ||
            child.getElementType() == JavaElementType.ENUM_CONSTANT) {
          CompositeElement modifierList = (CompositeElement)((CompositeElement)child).findChildByRole(ChildRole.MODIFIER_LIST);
          while (true) {
            ASTNode modifier = modifierList.findChildByType(MODIFIERS_TO_REMOVE_IN_ENUM_BIT_SET);
            if (modifier == null) break;
            modifierList.deleteChildInternal(modifier);
          }
        }
      }
    }
    else if (psiClass.isInterface()) {
      final boolean level8OrHigher = PsiUtil.isLanguageLevel8OrHigher(psiClass);
      for (ASTNode child = first; child != afterLast; child = next) {
        next = child.getTreeNext();
        final IElementType childElementType = child.getElementType();
        if (childElementType == JavaElementType.METHOD || childElementType == JavaElementType.FIELD) {
          CompositeElement modifierList = (CompositeElement)((CompositeElement)child).findChildByRole(ChildRole.MODIFIER_LIST);
          final TokenSet removeModifiersBitSet = level8OrHigher && childElementType == JavaElementType.METHOD
                                                 ? MODIFIERS_TO_REMOVE_IN_INTERFACE_BIT_SET_18_METHOD
                                                 : MODIFIERS_TO_REMOVE_IN_INTERFACE_BIT_SET;
          while (true) {
            ASTNode modifier = modifierList.findChildByType(removeModifiersBitSet);
            if (modifier == null) break;
            modifierList.deleteChildInternal(modifier);
          }
        }
      }
    }

    final TreeElement firstAdded = super.addInternal(first, last, anchor, before);
    if (firstAdded.getElementType() == ENUM_CONSTANT) {
      final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
      for (ASTNode child = ((ASTNode)first).getTreeNext(); child != null; child = child.getTreeNext()) {
        final IElementType elementType = child.getElementType();
        if (elementType == COMMA || elementType == SEMICOLON) break;
        if (elementType == ENUM_CONSTANT) {
          TreeElement comma = Factory.createSingleLeafElement(COMMA, ",", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, first, Boolean.FALSE);
          break;
        }
      }

      for (ASTNode child = ((ASTNode)first).getTreePrev(); child != null; child = child.getTreePrev()) {
        final IElementType elementType = child.getElementType();
        if (elementType == COMMA || elementType == SEMICOLON) break;
        if (elementType == ENUM_CONSTANT) {
          TreeElement comma = Factory.createSingleLeafElement(COMMA, ",", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, child, Boolean.FALSE);
          break;
        }
      }
    }
    return firstAdded;
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (isEnum() && child.getElementType() == ENUM_CONSTANT) {
      JavaSourceUtil.deleteSeparatingComma(this, child);
    }

    if (child.getElementType() == FIELD) {
      final ASTNode nextField = TreeUtil.findSibling(child.getTreeNext(), FIELD);
      if (nextField != null && ((PsiField)nextField.getPsi()).getTypeElement().equals(((PsiField)child.getPsi()).getTypeElement())) {
        final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
        final ASTNode modifierList = child.findChildByType(MODIFIER_LIST);
        if (modifierList != null) {
          LeafElement whitespace = Factory.createSingleLeafElement(WHITE_SPACE, " ", 0, 1, treeCharTab, getManager());
          final ASTNode first = nextField.getFirstChildNode();
          nextField.addChild(whitespace, first);
          final ASTNode typeElement = child.findChildByType(TYPE);
          if (typeElement == null) {
            final TreeElement modifierListCopy = ChangeUtil.copyElement((TreeElement)modifierList, treeCharTab);
            nextField.addChild(modifierListCopy, whitespace);
          } else {
            ASTNode run = modifierList;
            do {
              final TreeElement copy = ChangeUtil.copyElement((TreeElement)run, treeCharTab);
              nextField.addChild(copy, whitespace);
              if (run == typeElement) break; else run = run.getTreeNext();
            } while(true);
          }
        }
      }
    }

    super.deleteChildInternal(child);
  }

  public boolean isEnum() {
    final ASTNode keyword = findChildByRole(ChildRole.CLASS_OR_INTERFACE_KEYWORD);
    return keyword != null && keyword.getElementType() == ENUM_KEYWORD;
  }

  public boolean isAnnotationType() {
    return findChildByRole(ChildRole.AT) != null;
  }

  @Override
  public ASTNode findChildByRole(int role) {
    assert ChildRole.isUnique(role);

    switch (role) {
      default:
        return null;

      case ChildRole.DOC_COMMENT:
        return PsiImplUtil.findDocComment(this);

      case ChildRole.ENUM_CONSTANT_LIST_DELIMITER:
        return isEnum() ? findEnumConstantListDelimiter() : null;

      case ChildRole.MODIFIER_LIST:
        return findChildByType(MODIFIER_LIST);

      case ChildRole.EXTENDS_LIST:
        return findChildByType(EXTENDS_LIST);

      case ChildRole.IMPLEMENTS_LIST:
        return findChildByType(IMPLEMENTS_LIST);

      case ChildRole.TYPE_PARAMETER_LIST:
        return findChildByType(TYPE_PARAMETER_LIST);

      case ChildRole.CLASS_OR_INTERFACE_KEYWORD:
        for (ASTNode child = getFirstChildNode(); child != null; child = child.getTreeNext()) {
          if (CLASS_KEYWORD_BIT_SET.contains(child.getElementType())) return child;
        }
        String message = "Node not found. Immediate children are:---\n";
        for (ASTNode child = getFirstChildNode(); child != null; child = child.getTreeNext()) {
          message += child.getClass() + "(" + child.getElementType() + ") " + child + "\n";
        }
        message += "---";

        LOG.error(message);
        return null;

      case ChildRole.NAME:
        return findChildByType(IDENTIFIER);

      case ChildRole.LBRACE:
        return findChildByType(LBRACE);

      case ChildRole.RBRACE:
        return TreeUtil.findChildBackward(this, RBRACE);

      case ChildRole.AT:
        ASTNode modifierList = findChildByRole(ChildRole.MODIFIER_LIST);
        if (modifierList != null) {
          ASTNode treeNext = modifierList.getTreeNext();
          if (treeNext != null) {
            treeNext = PsiImplUtil.skipWhitespaceAndComments(treeNext);
            if (treeNext.getElementType() == AT) return treeNext;
          }
        }
        return null;
    }
  }

  private ASTNode findEnumConstantListDelimiter() {
    ASTNode candidate = findEnumConstantListDelimiterPlace();
    return candidate != null && candidate.getElementType() == SEMICOLON ? candidate : null;
  }

  @Nullable
  public ASTNode findEnumConstantListDelimiterPlace() {
    final ASTNode first = findChildByRole(ChildRole.LBRACE);
    if (first == null) return null;
    for (ASTNode child = first.getTreeNext(); child != null; child = child.getTreeNext()) {
      final IElementType childType = child.getElementType();
      if (PsiImplUtil.isWhitespaceOrComment(child) || childType == ERROR_ELEMENT || childType == ENUM_CONSTANT) {
      }
      else if (childType == COMMA) {
      }
      else if (childType == SEMICOLON) {
        return child;
      }
      else {
        return PsiImplUtil.skipWhitespaceAndCommentsBack(child.getTreePrev());
      }
    }

    return null;
  }

  @Override
  public int getChildRole(ASTNode child) {
    assert child.getTreeParent() == this;

    IElementType i = child.getElementType();
    if (i == SEMICOLON) {
      if (!isEnum()) return ChildRoleBase.NONE;
      if (child == findEnumConstantListDelimiter()) {
        return ChildRole.ENUM_CONSTANT_LIST_DELIMITER;
      }
      else {
        return ChildRoleBase.NONE;
      }
    }
    else if (i == CLASS) {
      return ChildRole.CLASS;
    }
    else if (i == FIELD) {
      return ChildRole.FIELD;
    }
    else if (i == METHOD || i == ANNOTATION_METHOD) {
      return ChildRole.METHOD;
    }
    else if (i == CLASS_INITIALIZER) {
      return ChildRole.CLASS_INITIALIZER;
    }
    else if (i == TYPE_PARAMETER_LIST) {
      return ChildRole.TYPE_PARAMETER_LIST;
    }
    else if (i == JavaDocElementType.DOC_COMMENT) {
      return getChildRole(child, ChildRole.DOC_COMMENT);
    }
    else if (ElementType.JAVA_PLAIN_COMMENT_BIT_SET.contains(i)) {
      return ChildRoleBase.NONE;
    }
    else if (i == MODIFIER_LIST) {
      return ChildRole.MODIFIER_LIST;
    }
    else if (i == EXTENDS_LIST) {
      return ChildRole.EXTENDS_LIST;
    }
    else if (i == IMPLEMENTS_LIST) {
      return ChildRole.IMPLEMENTS_LIST;
    }
    else if (ElementType.CLASS_KEYWORD_BIT_SET.contains(i)) {
      return getChildRole(child, ChildRole.CLASS_OR_INTERFACE_KEYWORD);
    }
    else if (i == IDENTIFIER) {
      return getChildRole(child, ChildRole.NAME);
    }
    else if (i == LBRACE) {
      return getChildRole(child, ChildRole.LBRACE);
    }
    else if (i == RBRACE) {
      return getChildRole(child, ChildRole.RBRACE);
    }
    else if (i == COMMA) {
      return ChildRole.COMMA;
    }
    else if (i == AT) {
      return ChildRole.AT;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }
}
