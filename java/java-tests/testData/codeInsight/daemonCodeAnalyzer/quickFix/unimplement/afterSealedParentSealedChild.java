// "Unimplement Class" "true"
sealed class A {}

sealed class B permits C {}

final class C extends B {}