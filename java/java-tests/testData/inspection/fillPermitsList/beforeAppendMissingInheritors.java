// "Add missing inheritors to permits list" "true"

sealed class A<caret> permits C {}

final class B extends A {}

final class C extends A {}