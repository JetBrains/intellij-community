package com.intellij.json.formatter;

import com.intellij.formatting.*;
import com.intellij.json.JsonElementTypes;
import com.intellij.json.JsonLanguage;
import com.intellij.json.JsonParserDefinition;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonPsiUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.json.JsonParserDefinition.JSON_BRACES;
import static com.intellij.json.JsonParserDefinition.JSON_BRACKETS;
import static com.intellij.json.formatter.JsonCodeStyleSettings.PropertyAlignment.ALIGN_ON_COLON;
import static com.intellij.json.formatter.JsonCodeStyleSettings.PropertyAlignment.ALIGN_ON_VALUE;

/**
 * @author Mikhail Golubev
 */
public class JsonBlock implements ASTBlock {
  private static final TokenSet OPEN_BRACES = TokenSet.create(JsonElementTypes.L_BRACKET, JsonElementTypes.L_CURLY);
  private static final TokenSet CLOSE_BRACES = TokenSet.create(JsonElementTypes.R_BRACKET, JsonElementTypes.R_CURLY);
  private static final TokenSet BRACES = TokenSet.orSet(OPEN_BRACES, CLOSE_BRACES);

  private final JsonBlock myParent;

  private final ASTNode myNode;
  private final PsiElement myPsiElement;
  private final Alignment myAlignment;
  private final Indent myIndent;
  private final Wrap myWrap;
  private final CodeStyleSettings mySettings;
  private final SpacingBuilder mySpacingBuilder;
  // lazy initialized on first call to #getSubBlocks()
  private List<Block> mySubBlocks = null;

  private final Alignment myChildAlignment = Alignment.createAlignment();

  private final Alignment myPropertyValueAlignment;
  private final Wrap myChildWrap;

  public JsonBlock(@Nullable JsonBlock parent,
                   @NotNull ASTNode node,
                   @NotNull CodeStyleSettings settings,
                   @Nullable Alignment alignment,
                   @NotNull Indent indent,
                   @Nullable Wrap wrap) {
    myParent = parent;
    myNode = node;
    myPsiElement = node.getPsi();
    myAlignment = alignment;
    myIndent = indent;
    myWrap = wrap;
    mySettings = settings;

    mySpacingBuilder = JsonFormattingBuilderModel.createSpacingBuilder(settings);

    if (myPsiElement instanceof JsonObject) {
      myChildWrap = Wrap.createWrap(getCustomSettings().OBJECT_WRAPPING, false);
    }
    else if (myPsiElement instanceof JsonArray) {
      myChildWrap = Wrap.createWrap(getCustomSettings().ARRAY_WRAPPING, false);
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

  @NotNull
  @Override
  public TextRange getTextRange() {
    return myNode.getTextRange();
  }

  @NotNull
  @Override
  public List<Block> getSubBlocks() {
    if (mySubBlocks == null) {
      mySubBlocks = ContainerUtil.mapNotNull(myNode.getChildren(null), new Function<ASTNode, Block>() {
        @Override
        public Block fun(ASTNode node) {
          if (isWhitespaceOrEmpty(node)) {
            return null;
          }
          return makeSubBlock(node);
        }
      });
    }
    return mySubBlocks;
  }

  private Block makeSubBlock(@NotNull ASTNode childNode) {
    final IElementType childNodeType = childNode.getElementType();

    Indent indent = Indent.getNoneIndent();
    Alignment alignment = null;
    Wrap wrap = null;

    JsonCodeStyleSettings customSettings = getCustomSettings();
    if (isContainer() && childNodeType != JsonElementTypes.COMMA && !BRACES.contains(childNodeType)) {
      assert myChildWrap != null && myChildAlignment != null;
      wrap = myChildWrap;
      alignment = myChildAlignment;
      indent = Indent.getNormalIndent();
    }
    // Handle properties alignment
    else if (myNode.getElementType() == JsonElementTypes.PROPERTY) {
      assert myParent.myNode.getElementType() == JsonElementTypes.OBJECT;
      assert myParent.myPropertyValueAlignment != null;
      if (childNode.getElementType() == JsonElementTypes.COLON && customSettings.PROPERTY_ALIGNMENT == ALIGN_ON_COLON) {
        alignment = myParent.myPropertyValueAlignment;
      }
      else if (JsonPsiUtil.isPropertyValue(childNode.getPsi()) && customSettings.PROPERTY_ALIGNMENT == ALIGN_ON_VALUE) {
        alignment = myParent.myPropertyValueAlignment;
      }
    }
    return new JsonBlock(this, childNode, mySettings, alignment, indent, wrap);
  }

  @Nullable
  @Override
  public Wrap getWrap() {
    return myWrap;
  }

  @Nullable
  @Override
  public Indent getIndent() {
    return myIndent;
  }

  @Nullable
  @Override
  public Alignment getAlignment() {
    return myAlignment;
  }

  @Nullable
  @Override
  public Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
    final CommonCodeStyleSettings commonSettings = getCommonSettings();
    final IElementType leftChildType = child1 instanceof JsonBlock ? ((JsonBlock)child1).myNode.getElementType() : null;
    final IElementType rightChildType = child2 instanceof JsonBlock ? ((JsonBlock)child2).myNode.getElementType() : null;
    // This causes braces/brackets to be on their own lines if whole object/array spans several lines.
    if (leftChildType != null && rightChildType != null) {
      if (JSON_BRACES.contains(leftChildType) ^ JSON_BRACES.contains(rightChildType)) {
        final int numSpaces = commonSettings.SPACE_WITHIN_BRACES ? 1 : 0;
        return Spacing.createDependentLFSpacing(numSpaces, numSpaces, myNode.getTextRange(),
                                                commonSettings.KEEP_LINE_BREAKS,
                                                commonSettings.KEEP_BLANK_LINES_IN_CODE);
      }
      else if (JSON_BRACKETS.contains(leftChildType) ^ JSON_BRACKETS.contains(rightChildType)) {
        final int numSpaces = commonSettings.SPACE_WITHIN_BRACKETS ? 1 : 0;
        return Spacing.createDependentLFSpacing(numSpaces, numSpaces, myNode.getTextRange(),
                                                commonSettings.KEEP_LINE_BREAKS,
                                                commonSettings.KEEP_BLANK_LINES_IN_CODE);
      }
    }
    return mySpacingBuilder.getSpacing(this, child1, child2);
  }

  @NotNull
  @Override
  public ChildAttributes getChildAttributes(int newChildIndex) {
    if (isContainer()) {
      return new ChildAttributes(Indent.getNormalIndent(), myChildAlignment);
    }
    // Will use continuation indent for cases like { "foo"<caret>  }
    return new ChildAttributes(null, null);
  }

  @Override
  public boolean isIncomplete() {
    IElementType nodeType = myNode.getElementType();
    ASTNode lastChildNode = myNode.getLastChildNode();
    if (nodeType == JsonElementTypes.OBJECT) {
      return lastChildNode != null && lastChildNode.getElementType() != JsonElementTypes.R_CURLY;
    }
    else if (nodeType == JsonElementTypes.ARRAY) {
      return lastChildNode != null && lastChildNode.getElementType() != JsonElementTypes.R_BRACKET;
    }
    else if (myPsiElement instanceof JsonProperty) {
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

  private boolean isContainer() {
    return JsonParserDefinition.JSON_CONTAINERS.contains(myNode.getElementType());
  }

  private JsonCodeStyleSettings getCustomSettings() {
    return mySettings.getCustomSettings(JsonCodeStyleSettings.class);
  }

  private CommonCodeStyleSettings getCommonSettings() {
    return mySettings.getCommonSettings(JsonLanguage.INSTANCE);
  }
}
