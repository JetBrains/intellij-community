sealed interface SuperSuper permits Super {}

sealed class Super implements SuperSuper permits Test {}

non-sealed class Test extends Super {}