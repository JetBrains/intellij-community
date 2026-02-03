// "Unimplement" "true-preview"
class A {}

sealed class B permits C {}

final class C extends B {}