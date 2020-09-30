sealed interface GrandParent {
}

public sealed class Parent<caret> implements GrandParent permits C, Child {}

final class Child extends Parent implements GrandParent {}

final class A implements GrandParent {}