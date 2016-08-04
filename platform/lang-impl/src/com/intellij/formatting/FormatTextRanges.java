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
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.ChangedRangesInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class FormatTextRanges implements FormattingRangesInfo {
  private final List<TextRange> myInsertedRanges;
  private final FormatRangesStorage myStorage = new FormatRangesStorage();

  public FormatTextRanges() {
    myInsertedRanges = null;
  }

  public FormatTextRanges(TextRange range, boolean processHeadingWhitespace) {
    myInsertedRanges = null;
    add(range, processHeadingWhitespace);
  }
  
  public FormatTextRanges(@NotNull ChangedRangesInfo changedRangesInfo) {
    changedRangesInfo.allChangedRanges.forEach((range) -> add(range, true));
    myInsertedRanges = changedRangesInfo.insertedRanges;
  }

  public void add(TextRange range, boolean processHeadingWhitespace) {
    myStorage.add(range, processHeadingWhitespace);
  }

  @Override
  public boolean isWhitespaceReadOnly(final @NotNull TextRange range) {
    return myStorage.isWhiteSpaceReadOnly(range);  
  }
  
  @Override
  public boolean isReadOnly(@NotNull TextRange range) {
    return myStorage.isReadOnly(range);
  }

  @Override
  public boolean isOnInsertedLine(int offset) {
    if (myInsertedRanges == null) return false;

    Optional<TextRange> enclosingRange = myInsertedRanges.stream()
      .filter((range) -> range.contains(offset))
      .findAny();

    return enclosingRange.isPresent();
  }
  
  public List<FormatTextRange> getRanges() {
    return myStorage.getRanges();
  }

  public FormatTextRanges ensureNonEmpty() {
    FormatTextRanges result = new FormatTextRanges();
    for (FormatTextRange range : myStorage.getRanges()) {
      if (range.isProcessHeadingWhitespace()) {
        result.add(range.getNonEmptyTextRange(), true);
      }
      else {
        result.add(range.getTextRange(), false);
      }
    }
    return result;
  }

  public boolean isEmpty() {
    return myStorage.isEmpty();
  }

  public boolean isFullReformat(PsiFile file) {
    List<FormatTextRange> ranges = myStorage.getRanges();
    return ranges.size() == 1 && file.getTextRange().equals(ranges.get(0).getTextRange());
  }

  public List<TextRange> getTextRanges() {
    return myStorage
      .getRanges()
      .stream()
      .map(FormatTextRange::getTextRange)
      .collect(Collectors.toList());
  }
  
}