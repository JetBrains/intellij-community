// "final" "true-preview"
sealed class Parent permits Child {}

final class Child extends Parent {}