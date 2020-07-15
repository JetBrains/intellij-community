// "Add missing inheritors to permits list" "true"

sealed class A permits B, C {}

final class B extends A {}

final class C extends A {}