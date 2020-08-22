sealed interface SuperSuper permits Super, Test {}

sealed class Super implements SuperSuper permits Test {}

non-sealed class Test extends Super implements SuperSuper {}