sealed class Parent permits Foo, B<caret> {}

final class Foo extends Parent {}

final class Bar extends Parent {}

