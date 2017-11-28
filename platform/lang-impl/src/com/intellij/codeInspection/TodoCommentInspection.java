/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.intellij.codeInspection;

import com.intellij.ide.todo.TodoIndexPatternProvider;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.IndexPatternOccurrence;
import com.intellij.psi.search.searches.IndexPatternSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class TodoCommentInspection extends LocalInspectionTool {

  @Nullable
  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final List<TextRange> ranges = getTodoRanges(file);
    if (ranges.isEmpty()) {
      return null;
    }

    final List<ProblemDescriptor> result = new SmartList<>();
    final THashSet<PsiComment> comments = new THashSet<>();
    for (TextRange todoRange : ranges) {
      final int todoStart = todoRange.getStartOffset();
      final PsiComment comment = PsiTreeUtil.getParentOfType(file.findElementAt(todoStart), PsiComment.class, false);
      if (comment != null && comments.add(comment)) {
        final int commentStart = comment.getTextOffset();
        final TextRange range = new TextRange(todoStart - commentStart, todoRange.getEndOffset() - commentStart);
        result.add(manager.createProblemDescriptor(comment, range, InspectionsBundle.message("todo.comment.problem.descriptor"),
                                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly));
      }
    }
    return result.toArray(new ProblemDescriptor[result.size()]);
  }

  private static List<TextRange> getTodoRanges(@NotNull PsiFile file) {
    final TodoIndexPatternProvider todoIndexPatternProvider = TodoIndexPatternProvider.getInstance();
    assert todoIndexPatternProvider != null;
    final Collection<IndexPatternOccurrence> occurrences = IndexPatternSearch.search(file, todoIndexPatternProvider).findAll();
    final List<TextRange> ranges = occurrences.stream().map(o -> o.getTextRange()).collect(Collectors.toList());
    ranges.sort((a, b) -> Comparing.compare(a.getStartOffset(), b.getStartOffset()));
    return ranges;
  }
}