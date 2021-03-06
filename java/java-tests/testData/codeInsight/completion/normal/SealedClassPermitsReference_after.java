sealed class Parent permits Child {}

final class A extends Parent {}

final class Child extends Parent {}