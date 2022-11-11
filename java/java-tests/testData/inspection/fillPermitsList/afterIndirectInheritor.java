// "Add missing subclasses to the permits clause" "true-preview"

sealed class A permits B, C, D.E /*1*/ {}

sealed class B extends A permits D {}

final class C extends A {}

final class D extends B {
  non-sealed static class E extends A {}
}