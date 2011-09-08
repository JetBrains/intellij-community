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

/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.openapi.util.TextRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReferenceRange {
  private ReferenceRange() {
  }

  public static List<TextRange> getRanges(PsiReference ref) {
    if (ref instanceof MultiRangeReference) {
      return ((MultiRangeReference)ref).getRanges();
    }
    return Collections.singletonList(ref.getRangeInElement());
  }

  public static List<TextRange> getAbsoluteRanges(PsiReference ref) {
    final PsiElement elt = ref.getElement();
    final List<TextRange> relativeRanges = getRanges(ref);
    final List<TextRange> answer = new ArrayList<TextRange>(relativeRanges.size());
    final int parentOffset = elt.getTextRange().getStartOffset();
    for (TextRange relativeRange : relativeRanges) {
      answer.add(relativeRange.shiftRight(parentOffset));
    }
    return answer;
  }

  public static TextRange getRange(PsiReference ref) {
    if (ref instanceof MultiRangeReference) {
      final List<TextRange> ranges = ((MultiRangeReference)ref).getRanges();
      return new TextRange(ranges.get(0).getStartOffset(), ranges.get(ranges.size() - 1).getEndOffset());
    }

    return ref.getRangeInElement();
  }

  public static boolean containsOffsetInElement(PsiReference ref, int offset) {
    if (ref instanceof MultiRangeReference) {
      for (TextRange range : ((MultiRangeReference)ref).getRanges()) {
        if (range.containsOffset(offset)) return true;
      }

      return false;
    }
    TextRange rangeInElement = ref.getRangeInElement();
    return rangeInElement != null && rangeInElement.containsOffset(offset);
  }

  public static boolean containsRangeInElement(PsiReference ref, TextRange rangeInElement) {
    if (ref instanceof MultiRangeReference) {
      for (TextRange range : ((MultiRangeReference)ref).getRanges()) {
        if (range.contains(rangeInElement)) return true;
      }

      return false;
    }
    TextRange rangeInElement1 = ref.getRangeInElement();
    return rangeInElement1 != null && rangeInElement1.contains(rangeInElement);
  }
}
