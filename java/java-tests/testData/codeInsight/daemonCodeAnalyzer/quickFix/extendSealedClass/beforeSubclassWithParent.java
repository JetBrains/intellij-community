// "final" "false"
sealed class Parent permits C<caret>hild {}

class OtherParent {}

class Child extends OtherParent {}