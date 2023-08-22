// "Unimplement" "true-preview"
sealed class A permits B {}

sealed class B extends <caret>A permits C {}

final class C extends B {}