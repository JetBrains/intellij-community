// "Add missing subclasses to the permits clause" "true-preview"

sealed class A<caret> {}

final class D extends A {}

non-sealed class C extends A {}

sealed class F extends A {}

final class E extends F {}