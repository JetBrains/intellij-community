// "Unimplement" "true-preview"
sealed class A permits B {}

non-sealed class B extends <caret>A {}