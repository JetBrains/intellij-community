// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The spacing setting for a formatting model block. Indicates the number of spaces and/or
 *  line breaks that should be inserted between the specified children of the specified block.
 *
 * @see Block#getSpacing(Block, Block)
 */
public abstract class Spacing {
  /**
   * Creates a regular spacing setting instance.
   *
   * @param minSpaces      The minimum number of spaces that should be present between the blocks
   *                       to which the spacing setting instance is related. Spaces are inserted
   *                       if there are less than this amount of spaces in the document.
   * @param maxSpaces      The maximum number of spaces that should be present between the blocks
   *                       to which the spacing setting instance is related, or {@code Integer.MAX_VALUE}
   *                       if the number of spaces is not limited. Spaces are deleted if there are
   *                       more than this amount of spaces in the document.
   * @param minLineFeeds   The minimum number of line breaks that should be present between the blocks
   *                       to which the spacing setting instance is related.
   * @param keepLineBreaks Whether the existing line breaks between the blocks should be preserved.
   * @param keepBlankLines Whether the existing blank lines between the blocks should be preserved.
   * @return the spacing setting instance.
   */
  public static Spacing createSpacing(int minSpaces,
                                      int maxSpaces,
                                      int minLineFeeds,
                                      boolean keepLineBreaks,
                                      int keepBlankLines) {
    return Formatter.getInstance().createSpacing(minSpaces, maxSpaces, minLineFeeds, keepLineBreaks, keepBlankLines);
  }

  /**
   * Creates a regular spacing setting instance.
   *
   * @param minSpaces      The minimum number of spaces that should be present between the blocks
   *                       to which the spacing setting instance is related. Spaces are inserted
   *                       if there are less than this amount of spaces in the document.
   * @param maxSpaces      The maximum number of spaces that should be present between the blocks
   *                       to which the spacing setting instance is related, or {@code Integer.MAX_VALUE}
   *                       if the number of spaces is not limited. Spaces are deleted if there are
   *                       more than this amount of spaces in the document.
   * @param minLineFeeds   The minimum number of line breaks that should be present between the blocks
   *                       to which the spacing setting instance is related.
   * @param keepLineBreaks Whether the existing line breaks between the blocks should be preserved.
   * @param keepBlankLines Whether the existing blank lines between the blocks should be preserved.
   * @return the spacing setting instance.
   */
  public static Spacing createSpacing(int minSpaces,
                                      int maxSpaces,
                                      int minLineFeeds,
                                      boolean keepLineBreaks,
                                      int keepBlankLines,
                                      int prefLineFeeds) {
    return Formatter.getInstance().createSpacing(minSpaces, maxSpaces, minLineFeeds, keepLineBreaks, keepBlankLines, prefLineFeeds);
  }

  /**
   * Returns a spacing setting instance indicating that no line breaks or spaces can be
   * inserted or removed by the formatter between the specified two blocks.
   * @return the spacing setting instance.
   */
  public static Spacing getReadOnlySpacing() {
    return Formatter.getInstance().getReadOnlySpacing();
  }

  /**
   * Creates a spacing setting instance which inserts a line break if the specified text range also
   * contains a line break. Used for formatting rules like the "next line if wrapped" brace placement.
   *
   * @param minSpaces      The minimum number of spaces that should be present between the blocks
   *                       to which the spacing setting instance is related. Spaces are inserted
   *                       if there are less than this amount of spaces in the document.
   * @param maxSpaces      The maximum number of spaces that should be present between the blocks
   *                       to which the spacing setting instance is related, or {@code Integer.MAX_VALUE}
   *                       if the number of spaces is not limited. Spaces are deleted if there are
   *                       more than this amount of spaces in the document.
   * @param dependency     The text range checked for the presence of line breaks.
   * @param keepLineBreaks Whether the existing line breaks between the blocks should be preserved.
   * @param keepBlankLines Whether the existing blank lines between the blocks should be preserved.
   * @return the spacing setting instance.
   */
  public static Spacing createDependentLFSpacing(int minSpaces,
                                                 int maxSpaces,
                                                 @NotNull TextRange dependency,
                                                 boolean keepLineBreaks,
                                                 int keepBlankLines)
  {
    return createDependentLFSpacing(minSpaces, maxSpaces, dependency, keepLineBreaks, keepBlankLines, DependentSpacingRule.DEFAULT);
  }


  /**
   * Needed when implementing options like "Next line after '('" or "Place ')' on new line". This options works only if parameters are
   * wrapped, so previously dependent spacing was created on whole parameter list range, which does not work correctly with multiline
   * arguments such as lambdas and anonymous classes.
   *
   * Using this method we can create dependent spacing on multiple ranges between parameters and solve this problem
   */
  public static Spacing createDependentLFSpacing(int minSpaces,
                                                 int maxSpaces,
                                                 @NotNull List<TextRange> dependency,
                                                 boolean keepLineBreaks,
                                                 int keepBlankLines)
  {
    return Formatter.getInstance().createDependentLFSpacing(minSpaces, maxSpaces, dependency, keepLineBreaks, keepBlankLines, DependentSpacingRule.DEFAULT);
  }

  /**
   * Creates a spacing setting instance which uses settings from the given dependent spacing rule if the specified text range changes
   * its 'has line feed' status during formatting (new line feed is added and the range hasn't contained them before
   * or it contained line feed(s) and it was removed during formatting).
   * <p/>
   * Used for formatting rules like the "next line if wrapped" brace placement.
   *
   * @param minSpaces        The minimum number of spaces that should be present between the blocks
   *                         to which the spacing setting instance is related. Spaces are inserted
   *                         if there are less than this amount of spaces in the document.
   * @param maxSpaces        The maximum number of spaces that should be present between the blocks
   *                         to which the spacing setting instance is related, or {@code Integer.MAX_VALUE}
   *                         if the number of spaces is not limited. Spaces are deleted if there are
   *                         more than this amount of spaces in the document.
   * @param dependencyRange  The text range checked for the presence of line breaks.
   * @param keepLineBreaks   Whether the existing line breaks between the blocks should be preserved.
   * @param keepBlankLines   Whether the existing blank lines between the blocks should be preserved.
   * @param rule             settings to use if dependent region changes its 'contains line feed' status during formatting
   * @return                 the spacing setting instance for the given parameters
   */
  public static Spacing createDependentLFSpacing(int minSpaces,
                                                 int maxSpaces,
                                                 @NotNull TextRange dependencyRange,
                                                 boolean keepLineBreaks,
                                                 int keepBlankLines,
                                                 @NotNull DependentSpacingRule rule)
  {
    return Formatter.getInstance().createDependentLFSpacing(minSpaces, maxSpaces, dependencyRange, keepLineBreaks, keepBlankLines, rule);
  }

  /**
   * Creates a spacing setting instance which preserves the presence of spaces between the blocks but,
   * if spaces are present, may insert or delete the spaces. Used, for example, for HTML formatting
   * where the presence of a whitespace is significant but the specific number of whitespaces at a
   * given location is not.
   *
   * @param keepLineBreaks Whether the existing line breaks between the blocks should be preserved.
   * @param keepBlankLines Whether the existing blank lines between the blocks should be preserved.
   * @return the spacing setting instance.
   */
  public static Spacing createSafeSpacing(boolean keepLineBreaks,
                                          int keepBlankLines) {
    return Formatter.getInstance().createSafeSpacing(keepLineBreaks, keepBlankLines);
  }

  /**
   * Creates a spacing setting instance that keeps the second child on the first column if it was
   * there before the formatting, but may indent the second child if it was not in the first column.
   * Used for implementing the "Keep when Reformatting | Comment in first column" setting.
   *
   * @param minSpaces      The minimum number of spaces that should be present between the blocks
   *                       to which the spacing setting instance is related. Spaces are inserted
   *                       if there are less than this amount of spaces in the document.
   * @param maxSpaces      The maximum number of spaces that should be present between the blocks
   *                       to which the spacing setting instance is related, or {@code Integer.MAX_VALUE}
   *                       if the number of spaces is not limited. Spaces are deleted if there are
   *                       more than this amount of spaces in the document.
   * @param keepLineBreaks Whether the existing line breaks between the blocks should be preserved.
   * @param keepBlankLines Whether the existing blank lines between the blocks should be preserved.
   * @return the spacing setting instance.
   */
  public static Spacing createKeepingFirstColumnSpacing(final int minSpaces,
                                                        final int maxSpaces,
                                                        final boolean keepLineBreaks,
                                                        final int keepBlankLines) {
    return Formatter.getInstance().createKeepingFirstColumnSpacing(minSpaces, maxSpaces, keepLineBreaks, keepBlankLines);
  }
}
