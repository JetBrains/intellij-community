// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * Base class for problems returned by local and global inspection tools.
 *
 * @author anna
 * @since 6.0
 * @see InspectionManager#createProblemDescriptor(String, QuickFix[])
 */
public interface CommonProblemDescriptor {
  Comparator<CommonProblemDescriptor> DESCRIPTOR_COMPARATOR = (c1, c2) -> {
    if (c1 instanceof ProblemDescriptor && c2 instanceof ProblemDescriptor) {
      int diff = ((ProblemDescriptor)c2).getLineNumber() - ((ProblemDescriptor)c1).getLineNumber();
      if (diff != 0) {
        return diff;
      }

      diff = PsiUtilCore.compareElementsByPosition(((ProblemDescriptor)c2).getPsiElement(), ((ProblemDescriptor)c1).getPsiElement());
      if (diff != 0) {
        return diff;
      }
    }
    return c1.getDescriptionTemplate().compareTo(c2.getDescriptionTemplate());
  };

  CommonProblemDescriptor[] EMPTY_ARRAY = new CommonProblemDescriptor[0];
  ArrayFactory<CommonProblemDescriptor> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new CommonProblemDescriptor[count];

  /**
   * Returns the template (text or html) from which the problem description is built.
   * The template may contain special markers:
   *
   * 1. {@code #ref} is replaced with the text of the element in which the problem has been found;
   *
   * 2. {@code #loc} is replaced with the filename and line number in exported inspection results and ignored when viewing within IDEA;
   *
   * 3. {@code #treeend} is used as cut-symbol for template when it's shown inside inspection result tree.
   * So any content after this marker is not visible in the tree node.
   *
   * @return the template for the problem description.
   */
  @NotNull
  String getDescriptionTemplate();

  /**
   * Returns the quickfixes for the problem.
   *
   * @return the list of quickfixes registered for the problem.
   */
  @Nullable
  QuickFix[] getFixes();
}
