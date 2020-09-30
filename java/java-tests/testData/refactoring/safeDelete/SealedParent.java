sealed interface GrandParent1 permits Parent, A {}

sealed class GrandParent2 permits Parent {}

sealed class <caret>Parent extends GrandParent2 implements GrandParent1 permits A, B {}

non-sealed class A extends Parent implements GrandParent1 {}

final class B extends Parent {}