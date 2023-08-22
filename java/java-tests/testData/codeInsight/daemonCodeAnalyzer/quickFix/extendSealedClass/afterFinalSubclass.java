// "Make 'Child' extend 'Parent'" "true-preview"
sealed class Parent permits Child {}

final class Child extends Parent {}