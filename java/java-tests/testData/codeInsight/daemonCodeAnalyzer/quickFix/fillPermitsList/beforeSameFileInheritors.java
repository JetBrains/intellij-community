// "Add missing inheritors to permits list" "true"

sealed class A<caret> {}

final class D extends A {}

non-sealed class C extends A {}

sealed class F extends A {}