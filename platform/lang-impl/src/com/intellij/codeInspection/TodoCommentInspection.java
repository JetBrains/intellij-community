/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.ide.todo.TodoIndexPatternProvider;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternOccurrence;
import com.intellij.psi.search.searches.IndexPatternSearch;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.codeInspection.options.OptPane.*;

@ApiStatus.Internal
public final class TodoCommentInspection extends LocalInspectionTool {

  public boolean onlyWarnOnEmpty = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("onlyWarnOnEmpty", LangBundle.message("todo.comment.only.warn.on.empty.option")));
  }

  @Override
  public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final List<TextRange> ranges = getTodoRanges(file);
    if (ranges.isEmpty()) {
      return null;
    }

    final List<ProblemDescriptor> result = new SmartList<>();
    int lastEndOffset = -1;
    for (TextRange todoRange : ranges) {
      final int todoStart = todoRange.getStartOffset();
      final int todoEnd = todoRange.getEndOffset();
      if (todoStart < lastEndOffset) continue;
      PsiElement element = file.findElementAt(todoStart);
      while (element != null && element.getTextRange().getEndOffset() < todoEnd) element = element.getParent();
      if (element != null) {
        final int elementStart = element.getTextRange().getStartOffset();
        final TextRange range = new TextRange(todoStart - elementStart, todoEnd - elementStart);
        final String message = onlyWarnOnEmpty
                               ? LangBundle.message("todo.comment.without.details.problem.descriptor")
                               : LangBundle.message("todo.comment.problem.descriptor");
        result.add(manager.createProblemDescriptor(element, range, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly));
        lastEndOffset = todoEnd;
      }
    }
    return result.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  private List<TextRange> getTodoRanges(@NotNull PsiFile file) {
    final List<String> excludedWords = new SmartList<>();
    final StringBuilder fileText = new StringBuilder();
    final TodoIndexPatternProvider todoIndexPatternProvider = TodoIndexPatternProvider.getInstance();
    final Collection<IndexPatternOccurrence> occurrences =
      IndexPatternSearch.search(file, todoIndexPatternProvider, TodoConfiguration.getInstance().isMultiLine()).findAll();
    return occurrences.stream()
      .map(occurrence -> {
        TextRange mainRange = occurrence.getTextRange();
        List<TextRange> additionalRanges = occurrence.getAdditionalTextRanges();
        return additionalRanges.isEmpty()
               ? mainRange
               : new TextRange(mainRange.getStartOffset(),
                               additionalRanges.get(additionalRanges.size() - 1).getEndOffset());
      })
      .filter(range -> {
        if (!onlyWarnOnEmpty) return true;
        if (excludedWords.isEmpty()) {
          for (IndexPattern pattern : todoIndexPatternProvider.getIndexPatterns()) {
            excludedWords.addAll(pattern.getWordsToFindFirst());
          }
        }
        if (fileText.isEmpty()) fileText.append(file.getText());
        final CharSequence text = range.subSequence(fileText);
        outer: for (String word : StringUtil.getWordsIn(text.toString())) {
          for (String excludedWord : excludedWords) {
            if (word.equalsIgnoreCase(excludedWord)) {
              continue outer;
            }
          }
          return false;
        }
        return true;
      })
      .sorted(Comparator.comparingInt(TextRange::getStartOffset))
      .collect(Collectors.toList());
  }
}