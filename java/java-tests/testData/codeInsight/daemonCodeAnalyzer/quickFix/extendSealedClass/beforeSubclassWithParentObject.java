// "Make 'Child' extend 'Parent'|->final" "true-preview"
sealed class Parent permits C<caret>hild {}

class Child extends Object {}