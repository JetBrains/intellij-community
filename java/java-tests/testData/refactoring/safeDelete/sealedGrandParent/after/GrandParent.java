sealed interface GrandParent permits A, C, Child {
}

final class Child implements GrandParent {}

final class A implements GrandParent {}