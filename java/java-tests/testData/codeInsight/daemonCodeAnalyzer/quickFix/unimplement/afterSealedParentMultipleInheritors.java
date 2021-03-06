// "Unimplement Class" "true"
sealed class A permits C {}

class B {}

final class C extends A {}