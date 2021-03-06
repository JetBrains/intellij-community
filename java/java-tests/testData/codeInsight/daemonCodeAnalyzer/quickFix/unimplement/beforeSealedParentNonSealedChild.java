// "Unimplement Class" "true"
sealed class A permits B {}

non-sealed class B extends <caret>A {}