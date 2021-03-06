sealed class Parent permits Foo, Bar {}

final class Foo extends Parent {}

final class Bar extends Parent {}

