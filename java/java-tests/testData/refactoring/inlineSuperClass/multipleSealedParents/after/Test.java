sealed interface SuperSuper permits Super, Test {}

final class Super implements SuperSuper {}

non-sealed class Test implements SuperSuper {}