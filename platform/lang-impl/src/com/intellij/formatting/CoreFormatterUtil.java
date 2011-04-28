/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Contains utility methods for core formatter processing.
 * 
 * @author Denis Zhdanov
 * @since 4/28/11 4:16 PM
 */
public class CoreFormatterUtil {

  private CoreFormatterUtil() {
  }

  /**
   * Checks if there is an {@link AlignmentImpl} object that should be used during adjusting
   * {@link AbstractBlockWrapper#getWhiteSpace() white space} of the given block.
   * 
   * @param block     target block
   * @return          alignment object to use during adjusting white space of the given block if any; <code>null</code> otherwise
   */
  @Nullable
  public static AlignmentImpl getAlignment(final @NotNull AbstractBlockWrapper block) {
    AbstractBlockWrapper current = block;
    while (true) {
      AlignmentImpl alignment = current.getAlignment();
      if (alignment == null || alignment.getOffsetRespBlockBefore(block) == null) {
        current = current.getParent();
        if (current == null || current.getStartOffset() != block.getStartOffset()) {
          return null;
        }
      }
      else {
        return alignment;
      }
    }
  }
  
  /**
   * Calculates number of non-line feed symbols before the given wrapped block.
   * <p/>
   * <b>Example:</b>
   * <pre>
   *      whitespace<sub>11</sub> block<sub>11</sub> whitespace<sub>12</sub> block<sub>12</sub>
   *      whitespace<sub>21</sub> block<sub>21</sub> whitespace<sub>22</sub> block<sub>22</sub>
   * </pre>
   * <p/>
   * Suppose this method is called with the wrapped <code>'block<sub>22</sub>'</code> and <code>'whitespace<sub>21</sub>'</code>
   * contains line feeds but <code>'whitespace<sub>22</sub>'</code> is not. This method returns number of symbols
   * from <code>'whitespace<sub>21</sub>'</code> after its last line feed symbol plus number of symbols at
   * <code>block<sub>21</sub></code> plus number of symbols at <code>whitespace<sub>22</sub></code>.
   *
   * @param block target wrapped block to be used at a boundary during counting non-line feed symbols to the left of it
   * @return non-line feed symbols to the left of the given wrapped block
   */
  public static int getOffsetBefore(@Nullable LeafBlockWrapper block) {
    if (block != null) {
      int result = 0;
      while (true) {
        final WhiteSpace whiteSpace = block.getWhiteSpace();
        result += whiteSpace.getTotalSpaces();
        if (whiteSpace.containsLineFeeds()) {
          return result;
        }
        block = block.getPreviousBlock();
        if (block == null) return result;
        result += block.getSymbolsAtTheLastLine();
        if (block.containsLineFeeds()) return result;
      }
    }
    else {
      return -1;
    }
  }

  /**
   * Tries to find the closest block that starts before the given block and contains line feeds.
   *
   * @return closest block to the given block that contains line feeds if any; <code>null</code> otherwise
   */
  @Nullable
  public static AbstractBlockWrapper getPreviousIndentedBlock(@NotNull AbstractBlockWrapper block) {
    AbstractBlockWrapper current = block.getParent();
    while (current != null) {
      if (current.getStartOffset() != block.getStartOffset() && current.getWhiteSpace().containsLineFeeds()) return current;
      if (current.getParent() != null) {
        AbstractBlockWrapper prevIndented = current.getParent().getPrevIndentedSibling(current);
        if (prevIndented != null) return prevIndented;

      }
      current = current.getParent();
    }
    return null;
  }
}
