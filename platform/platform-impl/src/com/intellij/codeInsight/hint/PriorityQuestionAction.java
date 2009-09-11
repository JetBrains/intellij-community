package com.intellij.codeInsight.hint;

/**
 * @author cdr
 */
public interface PriorityQuestionAction extends QuestionAction {
  int getPriority(); // hint with higher priority overlaps hint with lower priority
}
