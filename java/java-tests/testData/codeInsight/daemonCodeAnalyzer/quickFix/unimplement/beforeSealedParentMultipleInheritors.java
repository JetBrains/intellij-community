// "Unimplement" "true-preview"
sealed class A permits B, C {}

non-sealed class B extends A<caret> {}

final class C extends A {}