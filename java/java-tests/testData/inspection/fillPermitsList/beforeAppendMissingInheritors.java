// "Add missing subclasses to the permits clause" "true"

sealed class A<caret> permits C {}

final class B extends A {}

final class C extends A {}