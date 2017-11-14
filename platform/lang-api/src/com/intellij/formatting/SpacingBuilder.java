package com.intellij.formatting;

import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class SpacingBuilder {
  private static Logger LOG = Logger.getInstance(SpacingBuilder.class);
  
  private static class SpacingRule {
    protected final RuleCondition myRuleCondition;
    protected final int myMinSpaces;
    protected final int myMaxSpaces;
    protected final int myMinLF;
    protected final boolean myKeepLineBreaks;
    protected final int myKeepBlankLines;

    private SpacingRule(@NotNull RuleCondition condition, int minSpaces, int maxSpaces, int minLF, boolean keepLineBreaks, int keepBlankLines) {
      myRuleCondition = condition;
      myMinSpaces = minSpaces;
      myMaxSpaces = maxSpaces;
      myMinLF = minLF;
      myKeepLineBreaks = keepLineBreaks;
      myKeepBlankLines = keepBlankLines;
    }

    public boolean matches(@NotNull ASTBlock parentBlock, @NotNull ASTBlock childBlock1, @NotNull ASTBlock childBlock2) {
      return myRuleCondition.matches(parentBlock.getNode().getElementType(),
                                     childBlock1.getNode().getElementType(),
                                     childBlock2.getNode().getElementType());
    }

    public Spacing createSpacing(@NotNull ASTBlock parentBlock, @NotNull ASTBlock childBlock1, @NotNull ASTBlock childBlock2) {
      return Spacing.createSpacing(myMinSpaces, myMaxSpaces, myMinLF, myKeepLineBreaks, myKeepBlankLines);
    }
  }

  private static class DependentLFSpacingRule extends SpacingRule {
    public DependentLFSpacingRule(@NotNull RuleCondition condition,
                                  int minSpaces,
                                  int maxSpaces,
                                  boolean keepLineBreaks,
                                  int keepBlankLines) {
      super(condition, minSpaces, maxSpaces, 1, keepLineBreaks, keepBlankLines);
    }

    @Override
    public Spacing createSpacing(@NotNull ASTBlock parentBlock, @NotNull ASTBlock childBlock1, @NotNull ASTBlock childBlock2) {
      final TextRange range = parentBlock.getNode().getTextRange();
      return Spacing.createDependentLFSpacing(myMinSpaces, myMaxSpaces, range, myKeepLineBreaks, myKeepBlankLines);
    }
  }

  private static class RuleCondition {
    private final TokenSet myParentType;
    private final TokenSet myChild1Type;
    private final TokenSet myChild2Type;

    private RuleCondition(TokenSet parentType, TokenSet child1Type, TokenSet child2Type) {
      myParentType = parentType;
      myChild1Type = child1Type;
      myChild2Type = child2Type;
    }

    private boolean matches(@NotNull IElementType parentType, @NotNull IElementType firstChildType, @NotNull IElementType secondChildType) {
      return ((myParentType == null || myParentType.contains(parentType)) &&
              (myChild1Type == null || myChild1Type.contains(firstChildType)) &&
              (myChild2Type == null || myChild2Type.contains(secondChildType)));
    }
  }

  public class RuleBuilder {
    RuleCondition[] myConditions;

    private RuleBuilder(RuleCondition... conditions) {
      myConditions = conditions;
    }

    public SpacingBuilder none() {
      return spaces(0);
    }

    public SpacingBuilder spaceIf(boolean option) {
      return spaceIf(option, false);
    }

    /**
     * If {@code useParentDependentLFSpacing} is true and parent block spans multiple lines, insert single line break.
     * Otherwise insert whitespace block with exactly one or no spaces depending on value of {@code option} parameter.
     *
     * @see Spacing#createDependentLFSpacing
     */
    public SpacingBuilder spaceIf(boolean option, boolean useParentDependentLFSpacing) {
      return spaces(option ? 1 : 0, useParentDependentLFSpacing);
    }

    public SpacingBuilder spaces(int count) {
      return spaces(count, false);
    }

    /**
     * If {@code useParentDependentLFSpacing} is true and parent block spans multiple lines, insert single line break.
     * Otherwise insert whitespace block that contains as many spaces as specified via {@code count} parameter.
     *
     * @see Spacing#createDependentLFSpacing
     */
    public SpacingBuilder spaces(int count, boolean useParentDependentLFSpacing) {
      if (useParentDependentLFSpacing) {
        return parentDependentLFSpacing(count, count, myCodeStyleSettings.KEEP_LINE_BREAKS, myCodeStyleSettings.KEEP_BLANK_LINES_IN_CODE);
      }
      else {
        return spacing(count, count, 0, myCodeStyleSettings.KEEP_LINE_BREAKS, myCodeStyleSettings.KEEP_BLANK_LINES_IN_CODE);
      }
    }

    public SpacingBuilder blankLines(int count) {
      int blankLines = count + 1;
      for (RuleCondition condition : myConditions) {
        myRules.add(new SpacingRule(condition, 0, 0, blankLines,
                                    myCodeStyleSettings.KEEP_LINE_BREAKS, myCodeStyleSettings.KEEP_BLANK_LINES_IN_DECLARATIONS));
      }
      return SpacingBuilder.this;
    }

    public SpacingBuilder lineBreakInCodeIf(boolean option) {
      return option ? lineBreakInCode() : SpacingBuilder.this;
    }

    public SpacingBuilder lineBreakInCode() {
      for (RuleCondition condition : myConditions) {
        myRules.add(new SpacingRule(condition, 1, 0, 1,
                                    myCodeStyleSettings.KEEP_LINE_BREAKS, myCodeStyleSettings.KEEP_BLANK_LINES_IN_CODE));
      }
      return SpacingBuilder.this;
    }

    public SpacingBuilder lineBreakOrForceSpace(boolean lbOption, boolean spaceOption) {
      if (lbOption) {
        return lineBreakInCode();
      }

      int count = spaceOption ? 1 : 0;
      return spacing(count, count, 0, false, 0);
    }

    public SpacingBuilder spacing(int minSpaces, int maxSpaces, int minLF, boolean keepLineBreaks, int keepBlankLines) {
      for (RuleCondition condition : myConditions) {
        myRules.add(new SpacingRule(condition, minSpaces, maxSpaces,  minLF, keepLineBreaks, keepBlankLines));
      }
      return SpacingBuilder.this;
    }

    /**
     * Similar to {@link #spacing} but replaced by single line break, if parent block spans multiple lines.
     *
     * @see Spacing#createDependentLFSpacing
     */
    public SpacingBuilder parentDependentLFSpacing(int minSpaces, int maxSpaces, boolean keepLineBreaks, int keepBlankLines) {
      for (RuleCondition condition : myConditions) {
        myRules.add(new DependentLFSpacingRule(condition, minSpaces, maxSpaces, keepLineBreaks, keepBlankLines));
      }
      return SpacingBuilder.this;
    }
  }

  private final CommonCodeStyleSettings myCodeStyleSettings;
  private final List<SpacingRule> myRules = new ArrayList<>();

  /**
   * @param codeStyleSettings
   * @deprecated Use other constructors!
   */
  @SuppressWarnings("unused")
  @Deprecated
  public SpacingBuilder(CodeStyleSettings codeStyleSettings) {
    // TODO: remove deprecated method (v.14)
    myCodeStyleSettings = new CommonCodeStyleSettings(Language.ANY);
    LOG.error("The plugin calling this method uses deprecated API and must be updated.");
  }

  /**
   * Creates SpacingBuilder with given code style settings and language whose settings must be used. 
   * @param codeStyleSettings The root code style settings.
   * @param language          The language to obtain settings for.
   */
  public SpacingBuilder(@NotNull CodeStyleSettings codeStyleSettings, @NotNull Language language) {
    myCodeStyleSettings = codeStyleSettings.getCommonSettings(language);
  }

  /**
   * Creates SpacingBuilder with given language code style settings.
   * @param languageCodeStyleSettings The language code style settings. Note that {@code getLanguage()} method must not
   *                                  return null!
   */
  public SpacingBuilder(@NotNull CommonCodeStyleSettings languageCodeStyleSettings) {
    assert languageCodeStyleSettings.getLanguage() != null : "Only language code style settings are accepted (getLanguage() != null)";
    myCodeStyleSettings = languageCodeStyleSettings;
  }

  public RuleBuilder after(IElementType elementType) {
    return new RuleBuilder(new RuleCondition(null, TokenSet.create(elementType), null));
  }

  public RuleBuilder after(TokenSet tokenSet) {
    return new RuleBuilder(new RuleCondition(null, tokenSet, null));
  }

  public RuleBuilder afterInside(IElementType elementType, IElementType parentType) {
    return new RuleBuilder(new RuleCondition(TokenSet.create(parentType), TokenSet.create(elementType), null));
  }

  public RuleBuilder afterInside(IElementType elementType, TokenSet parentType) {
    return new RuleBuilder(new RuleCondition(parentType, TokenSet.create(elementType), null));
  }

  public RuleBuilder afterInside(TokenSet tokenSet, IElementType parentType) {
    return new RuleBuilder(new RuleCondition(TokenSet.create(parentType), tokenSet, null));
  }

  public RuleBuilder before(IElementType elementType) {
    return before(TokenSet.create(elementType));
  }

  public RuleBuilder before(TokenSet tokenSet) {
    return new RuleBuilder(new RuleCondition(null, null, tokenSet));
  }

  public RuleBuilder beforeInside(TokenSet tokenSet, IElementType parentType) {
    return new RuleBuilder(new RuleCondition(TokenSet.create(parentType), null, tokenSet));
  }

  public RuleBuilder beforeInside(IElementType elementType, IElementType parentType) {
    return new RuleBuilder(new RuleCondition(TokenSet.create(parentType), null, TokenSet.create(elementType)));
  }

  public RuleBuilder beforeInside(IElementType elementType, TokenSet parentTypes) {
    return new RuleBuilder(new RuleCondition(parentTypes, null, TokenSet.create(elementType)));
  }

  public RuleBuilder between(IElementType left, IElementType right) {
    return new RuleBuilder(new RuleCondition(null, TokenSet.create(left), TokenSet.create(right)));
  }

  public RuleBuilder between(IElementType left, TokenSet rightSet) {
    return new RuleBuilder(new RuleCondition(null, TokenSet.create(left), rightSet));
  }

  public RuleBuilder between(TokenSet leftSet, IElementType right) {
    return new RuleBuilder(new RuleCondition(null, leftSet, TokenSet.create(right)));
  }

  public RuleBuilder between(TokenSet leftType, TokenSet rightType) {
    return new RuleBuilder(new RuleCondition(null, leftType, rightType));
  }

  public RuleBuilder betweenInside(IElementType leftType, IElementType rightType, IElementType parentType) {
    return new RuleBuilder(new RuleCondition(TokenSet.create(parentType), TokenSet.create(leftType), TokenSet.create(rightType)));
  }

  public RuleBuilder betweenInside(TokenSet leftSet, TokenSet rightSet, IElementType parentType) {
    return new RuleBuilder(new RuleCondition(TokenSet.create(parentType), leftSet, rightSet));
  }

  public RuleBuilder withinPair(IElementType pairFirst, IElementType pairSecond) {
    RuleCondition before = new RuleCondition(null, TokenSet.create(pairFirst), null);
    RuleCondition after = new RuleCondition(null, null, TokenSet.create(pairSecond));
    return new RuleBuilder(before, after);
  }

  public RuleBuilder withinPairInside(IElementType pairFirst, IElementType pairSecond, IElementType parent) {
    TokenSet parentSet = TokenSet.create(parent);
    RuleCondition before = new RuleCondition(parentSet, TokenSet.create(pairFirst), null);
    RuleCondition after = new RuleCondition(parentSet, null, TokenSet.create(pairSecond));
    return new RuleBuilder(before, after);
  }

  public RuleBuilder around(IElementType elementType) {
    return around(TokenSet.create(elementType));
  }

  public RuleBuilder around(TokenSet tokenSet) {
    RuleCondition before = new RuleCondition(null, null, tokenSet);
    RuleCondition after = new RuleCondition(null, tokenSet, null);
    return new RuleBuilder(before, after);
  }

  public RuleBuilder aroundInside(TokenSet tokenSet, TokenSet parent) {
    RuleCondition before = new RuleCondition(parent, null, tokenSet);
    RuleCondition after = new RuleCondition(parent, tokenSet, null);
    return new RuleBuilder(before, after);
  }

  public RuleBuilder aroundInside(TokenSet tokenSet, IElementType parent) {
    RuleCondition before = new RuleCondition(TokenSet.create(parent), null, tokenSet);
    RuleCondition after = new RuleCondition(TokenSet.create(parent), tokenSet, null);
    return new RuleBuilder(before, after);
  }

  public RuleBuilder aroundInside(IElementType token, IElementType parent) {
    final TokenSet tokenSet = TokenSet.create(token);
    RuleCondition before = new RuleCondition(TokenSet.create(parent), null, tokenSet);
    RuleCondition after = new RuleCondition(TokenSet.create(parent), tokenSet, null);
    return new RuleBuilder(before, after);
  }

  public RuleBuilder aroundInside(IElementType token, TokenSet parent) {
    final TokenSet tokenSet = TokenSet.create(token);
    RuleCondition before = new RuleCondition(parent, null, tokenSet);
    RuleCondition after = new RuleCondition(parent, tokenSet, null);
    return new RuleBuilder(before, after);
  }

  public SpacingBuilder append(SpacingBuilder builder) {
    myRules.addAll(builder.myRules);
    return this;
  }

  @Nullable
  public Spacing getSpacing(Block parent, Block child1, Block child2) {
    if (!(parent instanceof ASTBlock) || !(child1 instanceof ASTBlock) || !(child2 instanceof ASTBlock)) {
      return null;
    }
    for (SpacingRule rule : myRules) {
      if (rule.matches((ASTBlock)parent, (ASTBlock)child1, (ASTBlock) child2)) {
        return rule.createSpacing((ASTBlock)parent, (ASTBlock)child1, (ASTBlock) child2);
      }
    }
    return null;
  }
}
