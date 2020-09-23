// "Extend 'Parent'" "true"
sealed class Parent permits C<caret>hild {}

final class Child {}