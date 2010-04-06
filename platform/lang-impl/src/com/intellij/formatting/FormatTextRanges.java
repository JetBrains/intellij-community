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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor;
import com.intellij.util.Function;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class FormatTextRanges {
  public static class FormatTextRange {
    private TextRange myRange;
    private final boolean myProcessHeadingWhitespace;

    public FormatTextRange(TextRange range, boolean processHeadingWhitespace) {
      myRange = range;
      myProcessHeadingWhitespace = processHeadingWhitespace;
    }

    public boolean isWhitespaceReadOnly(TextRange range) {
      if (range.getStartOffset() >= myRange.getEndOffset()) return true;
      if (myProcessHeadingWhitespace) {
        return range.getEndOffset() < myRange.getStartOffset();
      }
      else {
        return range.getEndOffset() <= myRange.getStartOffset();
      }
    }

    public int getStartOffset() {
      return myRange.getStartOffset();
    }

    public boolean isReadOnly(TextRange range, boolean rootIsRightBlock) {
      if (myRange.getStartOffset() >= range.getEndOffset() && rootIsRightBlock) {
        return false;
      }

      return range.getStartOffset() > myRange.getEndOffset() || range.getEndOffset() < myRange.getStartOffset();
    }

    public TextRange getTextRange() {
      return myRange;
    }

    public void setTextRange(TextRange range) {
      myRange = range;
    }

    public TextRange getNonEmptyTextRange() {
      return new TextRange(myRange.getStartOffset(), myRange.getStartOffset() == myRange.getEndOffset()
                                                     ? myRange.getEndOffset()+1
                                                     : myRange.getEndOffset());
    }

    @Override
    public String toString() {
      return myRange.toString() + (myProcessHeadingWhitespace ? "+" : "");
    }
  }

  private final List<FormatTextRange> myRanges = new ArrayList<FormatTextRange>();

  public FormatTextRanges() {
  }

  public FormatTextRanges(TextRange range, boolean processHeadingWhitespace) {
    add(range, processHeadingWhitespace);
  }

  public void add(TextRange range, boolean processHeadingWhitespace) {
    myRanges.add(new FormatTextRange(range, processHeadingWhitespace));
  }

  public boolean isWhitespaceReadOnly(TextRange range) {
    for (FormatTextRange formatTextRange : myRanges) {
      if (!formatTextRange.isWhitespaceReadOnly(range)) {
        return false;
      }
    }
    return true;
  }

  public boolean isReadOnly(TextRange range, boolean rootIsRightBlock) {
    for (FormatTextRange formatTextRange : myRanges) {
      if (!formatTextRange.isReadOnly(range, rootIsRightBlock)) {
        return false;
      }
    }
    return true;
  }

  public void preprocess(ASTNode fileNode) {
    for(FormatTextRange range: myRanges) {
      TextRange result = range.getTextRange();
      for(PreFormatProcessor processor: Extensions.getExtensions(PreFormatProcessor.EP_NAME)) {
        result = processor.process(fileNode, result);
      }
      range.setTextRange(result);
    }
  }

  public List<FormatTextRange> getRanges() {
    return myRanges;
  }

  public FormatTextRanges ensureNonEmpty() {
    FormatTextRanges result = new FormatTextRanges();
    for (FormatTextRange range : myRanges) {
      if (range.myProcessHeadingWhitespace) {
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
    return "FormatTextRanges{" + StringUtil.join(myRanges, new Function<FormatTextRange, String>() {
      public String fun(FormatTextRange formatTextRange) {
        return formatTextRange.toString();
      }
    }, ",");
  }
}
