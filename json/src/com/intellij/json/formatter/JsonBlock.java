// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.formatter;

import com.intellij.formatting.*;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonPsiUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.json.JsonElementTypes.*;
import static com.intellij.json.JsonTokenSets.JSON_CONTAINERS;
import static com.intellij.json.formatter.JsonCodeStyleSettings.ALIGN_PROPERTY_ON_COLON;
import static com.intellij.json.formatter.JsonCodeStyleSettings.ALIGN_PROPERTY_ON_VALUE;
import static com.intellij.json.psi.JsonPsiUtil.hasElementType;

/**
 * @author Mikhail Golubev
 */
public final class JsonBlock implements ASTBlock {
  private static final TokenSet JSON_OPEN_BRACES = TokenSet.create(L_BRACKET, L_CURLY);
  private static final TokenSet JSON_CLOSE_BRACES = TokenSet.create(R_BRACKET, R_CURLY);
  private static final TokenSet JSON_ALL_BRACES = TokenSet.orSet(JSON_OPEN_BRACES, JSON_CLOSE_BRACES);

  private final JsonBlock myParent;

  private final ASTNode myNode;
  private final PsiElement myPsiElement;
  private final Alignment myAlignment;
  private final Indent myIndent;
  private final Wrap myWrap;
  private final JsonCodeStyleSettings myCustomSettings;
  private final SpacingBuilder mySpacingBuilder;
  // lazy initialized on first call to #getSubBlocks()
  private List<Block> mySubBlocks = null;

  private final Alignment myPropertyValueAlignment;
  private final Wrap myChildWrap;

  public JsonBlock(@Nullable JsonBlock parent,
                   @NotNull ASTNode node,
                   @NotNull JsonCodeStyleSettings customSettings,
                   @Nullable Alignment alignment,
                   @NotNull Indent indent,
                   @Nullable Wrap wrap,
                   @NotNull SpacingBuilder spacingBuilder) {
    myParent = parent;
    myNode = node;
    myPsiElement = node.getPsi();
    myAlignment = alignment;
    myIndent = indent;
    myWrap = wrap;
    mySpacingBuilder = spacingBuilder;
    myCustomSettings = customSettings;

    if (myPsiElement instanceof JsonObject) {
      myChildWrap = Wrap.createWrap(myCustomSettings.OBJECT_WRAPPING, true);
    }
    else if (myPsiElement instanceof JsonArray) {
      myChildWrap = Wrap.createWrap(myCustomSettings.ARRAY_WRAPPING, true);
    }
    else {
      myChildWrap = null;
    }

    myPropertyValueAlignment = myPsiElement instanceof JsonObject ? Alignment.createAlignment(true) : null;
  }

  @Override
  public ASTNode getNode() {
    return myNode;
  }

  @Override
  public @NotNull TextRange getTextRange() {
    return myNode.getTextRange();
  }

  @Override
  public @NotNull List<Block> getSubBlocks() {
    if (mySubBlocks == null) {
      int propertyAlignment = myCustomSettings.PROPERTY_ALIGNMENT;
      ASTNode[] children = myNode.getChildren(null);
      mySubBlocks = new ArrayList<>(children.length);
      for (ASTNode child: children) {
        if (isWhitespaceOrEmpty(child)) continue;
        mySubBlocks.add(makeSubBlock(child, propertyAlignment));
      }
    }
    return mySubBlocks;
  }

  private Block makeSubBlock(@NotNull ASTNode childNode, int propertyAlignment) {
    Indent indent = Indent.getNoneIndent();
    Alignment alignment = null;
    Wrap wrap = null;

    if (hasElementType(myNode, JSON_CONTAINERS)) {
      if (hasElementType(childNode, COMMA)) {
        wrap = Wrap.createWrap(WrapType.NONE, true);
      }
      else if (!hasElementType(childNode, JSON_ALL_BRACES)) {
        assert myChildWrap != null;
        wrap = myChildWrap;
        indent = Indent.getNormalIndent();
      }
      else if (hasElementType(childNode, JSON_OPEN_BRACES)) {
        if (JsonPsiUtil.isPropertyValue(myPsiElement) && propertyAlignment == ALIGN_PROPERTY_ON_VALUE) {
          // WEB-13587 Align compound values on opening brace/bracket, not the whole block
          assert myParent != null && myParent.myParent != null && myParent.myParent.myPropertyValueAlignment != null;
          alignment = myParent.myParent.myPropertyValueAlignment;
        }
      }
    }
    // Handle properties alignment
    else if (hasElementType(myNode, PROPERTY) ) {
      assert myParent != null && myParent.myPropertyValueAlignment != null;
      if (hasElementType(childNode, COLON) && propertyAlignment == ALIGN_PROPERTY_ON_COLON) {
        alignment = myParent.myPropertyValueAlignment;
      }
      else if (JsonPsiUtil.isPropertyValue(childNode.getPsi()) && propertyAlignment == ALIGN_PROPERTY_ON_VALUE) {
        if (!hasElementType(childNode, JSON_CONTAINERS)) {
          alignment = myParent.myPropertyValueAlignment;
        }
      }
    }
    return new JsonBlock(this, childNode, myCustomSettings, alignment, indent, wrap, mySpacingBuilder);
  }

  @Override
  public @Nullable Wrap getWrap() {
    return myWrap;
  }

  @Override
  public @Nullable Indent getIndent() {
    return myIndent;
  }

  @Override
  public @Nullable Alignment getAlignment() {
    return myAlignment;
  }

  @Override
  public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
    return mySpacingBuilder.getSpacing(this, child1, child2);
  }

  @Override
  public @NotNull ChildAttributes getChildAttributes(int newChildIndex) {
    if (hasElementType(myNode, JSON_CONTAINERS)) {
      // WEB-13675: For some reason including alignment in child attributes causes
      // indents to consist solely of spaces when both USE_TABS and SMART_TAB
      // options are enabled.
      return new ChildAttributes(Indent.getNormalIndent(), null);
    }
    else if (myNode.getPsi() instanceof PsiFile) {
      return new ChildAttributes(Indent.getNoneIndent(), null);
    }
    // Will use continuation indent for cases like { "foo"<caret>  }
    return new ChildAttributes(null, null);
  }

  @Override
  public boolean isIncomplete() {
    final ASTNode lastChildNode = myNode.getLastChildNode();
    if (hasElementType(myNode, OBJECT)) {
      return lastChildNode != null && lastChildNode.getElementType() != R_CURLY;
    }
    else if (hasElementType(myNode, ARRAY)) {
      return lastChildNode != null && lastChildNode.getElementType() != R_BRACKET;
    }
    else if (hasElementType(myNode, PROPERTY)) {
      return ((JsonProperty)myPsiElement).getValue() == null;
    }
    return false;
  }

  @Override
  public boolean isLeaf() {
    return myNode.getFirstChildNode() == null;
  }

  private static boolean isWhitespaceOrEmpty(ASTNode node) {
    return node.getElementType() == TokenType.WHITE_SPACE || node.getTextLength() == 0;
  }
}
