/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;

import java.util.ArrayList;
import java.util.List;

public class FormatTextRanges {

  private final List<FormatTextRange> myRanges = new ArrayList<>();

  public FormatTextRanges() {
  }

  public FormatTextRanges(TextRange range, boolean processHeadingWhitespace) {
    add(range, processHeadingWhitespace);
  }

  public void add(TextRange range, boolean processHeadingWhitespace) {
    myRanges.add(new FormatTextRange(range, processHeadingWhitespace));
  }

  /**
   * Batches {@link FormatTextRange#isWhitespaceReadOnly(TextRange)} operation for all aggregated ranges.
   * <p/>
   * I.e. this method allows to check if given range has intersections with any of aggregated ranges.
   *
   * @param range     range to check
   * @return               <code>true</code> if given range doesn't have intersections with all aggregated ranges;
   *                             <code>false</code> if given range intersects at least one of aggregated ranges
   */
  public boolean isWhitespaceReadOnly(TextRange range) {
    for (FormatTextRange formatTextRange : myRanges) {
      if (!formatTextRange.isWhitespaceReadOnly(range)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Batches {@link FormatTextRange#isReadOnly(TextRange, boolean)} operation for all aggregated ranges.
   * <p/>
   * I.e. this method allows to check if given range has intersections with any of aggregated ranges.
   *
   * @param range                 range to check
   * @param rootIsRightBlock      flag to use during {@link FormatTextRange#isReadOnly(TextRange, boolean)} processing
   * @return                      <code>true</code> if given range doesn't have intersections with all aggregated ranges;
   *                              <code>false</code> if given range intersects at least one of aggregated ranges
   */
  public boolean isReadOnly(TextRange range, boolean rootIsRightBlock) {
    for (FormatTextRange formatTextRange : myRanges) {
      if (!formatTextRange.isReadOnly(range, rootIsRightBlock)) {
        return false;
      }
    }
    return true;
  }

  public List<FormatTextRange> getRanges() {
    return myRanges;
  }

  public FormatTextRanges ensureNonEmpty() {
    FormatTextRanges result = new FormatTextRanges();
    for (FormatTextRange range : myRanges) {
      if (range.isProcessHeadingWhitespace()) {
        result.add(range.getNonEmptyTextRange(), true);
      }
      else {
        result.add(range.getTextRange(), false);
      }
    }
    return result;
  }

  @Override
  public String toString() {
    return "FormatTextRanges{" + StringUtil.join(myRanges, StringUtil.createToStringFunction(FormatTextRange.class), ",");
  }
}