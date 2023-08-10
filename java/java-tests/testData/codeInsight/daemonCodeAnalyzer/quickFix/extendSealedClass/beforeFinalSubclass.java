// "Make 'Child' extend 'Parent'" "true-preview"
sealed class Parent permits C<caret>hild {}

final class Child {}