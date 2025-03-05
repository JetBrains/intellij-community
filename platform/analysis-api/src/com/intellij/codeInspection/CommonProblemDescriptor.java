// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * Base class for problems returned by local and global inspection tools.
 *
 * @author anna
 * @see InspectionManager#createProblemDescriptor(String, QuickFix[])
 */
public interface CommonProblemDescriptor {
  Comparator<CommonProblemDescriptor> DESCRIPTOR_COMPARATOR = (c1, c2) -> {
    if (c1 instanceof ProblemDescriptor pd1 && c2 instanceof ProblemDescriptor pd2) {
      int diff = Integer.compare(pd2.getLineNumber(), pd1.getLineNumber());
      if (diff != 0) {
        return diff;
      }
      diff = PsiUtilCore.compareElementsByPosition(pd2.getPsiElement(), pd1.getPsiElement());
      if (diff != 0) {
        return diff;
      }
      TextRange range1 = pd1.getTextRangeInElement();
      TextRange range2 = pd2.getTextRangeInElement();
      diff = Segment.BY_START_OFFSET_THEN_END_OFFSET.compare(range1 == null ? TextRange.EMPTY_RANGE : range1, range2 == null ? TextRange.EMPTY_RANGE : range2);
      if (diff != 0) {
        return diff;
      }
    }
    return c1.getDescriptionTemplate().compareTo(c2.getDescriptionTemplate());
  };

  CommonProblemDescriptor[] EMPTY_ARRAY = new CommonProblemDescriptor[0];
  ArrayFactory<CommonProblemDescriptor> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new CommonProblemDescriptor[count];

  /**
   * Returns the template (text or HTML) from which the problem description is built.
   * <p>
   * The template may contain special markers:
   * <ul>
   * <li>{@code #ref} is replaced with the text of the element in which the problem has been found</li>
   * <li>{@code #loc} is replaced with the filename and line number in exported inspection results and ignored when viewing within IDE</li>
   * <li>{@code #treeend} is used as cut-symbol for template when it's shown inside inspection result tree.
   * So any content after this marker is not visible in the tree node.</li>
   * </ul>
   * @return the template for the problem description.
   */
  @InspectionMessage
  @NotNull
  String getDescriptionTemplate();

  /**
   * Returns the quickfixes for the problem.
   *
   * @return the array of quickfixes registered for the problem.
   */
  @NotNull QuickFix @Nullable [] getFixes();
}
