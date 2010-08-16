/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.formatting;

import com.intellij.openapi.util.TextRange;

/**
 * The spacing setting for a formatting model block. Indicates the number of spaces and/or
 *  line breaks that should be inserted between the specified children of the specified block.
 *
 * @see Block#getSpacing(Block, Block)
 */

public abstract class Spacing {
  private static SpacingFactory myFactory;

  static void setFactory(SpacingFactory factory) {
    myFactory = factory;
  }

  /**
   * Creates a regular spacing setting instance.
   *
   * @param minSpaces      The minimum number of spaces that should be present between the blocks
   *                       to which the spacing setting instance is related. Spaces are inserted
   *                       if there are less than this amount of spaces in the document.
   * @param maxSpaces      The maximum number of spaces that should be present between the blocks
   *                       to which the spacing setting instance is related, or <code>Integer.MAX_VALUE</code>
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
    return myFactory.createSpacing(minSpaces, maxSpaces, minLineFeeds, keepLineBreaks, keepBlankLines);
  }

  /**
   * Creates a regular spacing setting instance.
   *
   * @param minSpaces      The minimum number of spaces that should be present between the blocks
   *                       to which the spacing setting instance is related. Spaces are inserted
   *                       if there are less than this amount of spaces in the document.
   * @param maxSpaces      The maximum number of spaces that should be present between the blocks
   *                       to which the spacing setting instance is related, or <code>Integer.MAX_VALUE</code>
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
    return myFactory.createSpacing(minSpaces, maxSpaces, minLineFeeds, keepLineBreaks, keepBlankLines, prefLineFeeds);
  }

  /**
   * Returns a spacing setting instance indicating that no line breaks or spaces can be
   * inserted or removed by the formatter between the specified two blocks.
   * @return the spacing setting instance.
   */
  public static Spacing getReadOnlySpacing() {
    return myFactory.getReadOnlySpacing();
  }

  /**
   * Creates a spacing setting instance which inserts a line break if the specified text range also
   * contains a line break. Used for formatting rules like the "next line if wrapped" brace placement.
   *
   * @param minSpaces      The minimum number of spaces that should be present between the blocks
   *                       to which the spacing setting instance is related. Spaces are inserted
   *                       if there are less than this amount of spaces in the document.
   * @param maxSpaces      The maximum number of spaces that should be present between the blocks
   *                       to which the spacing setting instance is related, or <code>Integer.MAX_VALUE</code>
   *                       if the number of spaces is not limited. Spaces are deleted if there are
   *                       more than this amount of spaces in the document.
   * @param dependance     The text range checked for the presense of line breaks.
   * @param keepLineBreaks Whether the existing line breaks between the blocks should be preserved.
   * @param keepBlankLines Whether the existing blank lines between the blocks should be preserved.
   * @return the spacing setting instance.
   */
  public static Spacing createDependentLFSpacing(int minSpaces,
                                                 int maxSpaces,
                                                 TextRange dependance,
                                                 boolean keepLineBreaks,
                                                 int keepBlankLines) {
    return myFactory.createDependentLFSpacing(minSpaces, maxSpaces, dependance, keepLineBreaks, keepBlankLines);
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
    return myFactory.createSafeSpacing(keepLineBreaks, keepBlankLines);
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
   *                       to which the spacing setting instance is related, or <code>Integer.MAX_VALUE</code>
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
    return myFactory.createKeepingFirstColumnSpacing(minSpaces, maxSpaces, keepLineBreaks, keepBlankLines);
  }
}
