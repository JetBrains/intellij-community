sealed interface GrandParent1 permits A, B {}

sealed class GrandParent2 permits A, B {}

non-sealed class A extends GrandParent2 implements GrandParent1 {}

final class B extends GrandParent2 implements GrandParent1 {}