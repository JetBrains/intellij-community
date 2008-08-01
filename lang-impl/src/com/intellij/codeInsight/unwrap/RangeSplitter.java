package com.intellij.codeInsight.unwrap;

import com.intellij.openapi.util.TextRange;

import java.util.List;
import java.util.ArrayList;

public class RangeSplitter {
  public static List<TextRange> split(TextRange target, List<TextRange> deviders) {
    List<TextRange> result = new ArrayList<TextRange>();
    result.add(target);

    for (TextRange devider : deviders) {
      List<TextRange> temp = new ArrayList<TextRange>();
      for (TextRange range : result) {
        if (!range.contains(devider)) {
          temp.add(range);
          continue;
        }

        if (range.getStartOffset() < devider.getStartOffset())
          temp.add(new TextRange(range.getStartOffset(), devider.getStartOffset()));

        if (range.getEndOffset() > devider.getEndOffset())
          temp.add(new TextRange(devider.getEndOffset(), range.getEndOffset()));
      }
      result = temp;
    }

    return result;
  }
}
