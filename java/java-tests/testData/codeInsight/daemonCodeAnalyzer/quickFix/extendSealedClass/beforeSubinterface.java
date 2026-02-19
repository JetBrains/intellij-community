// "Make 'Child' extend 'Parent'" "true-preview"
sealed interface Parent permits C<caret>hild {}

non-sealed interface Child {}