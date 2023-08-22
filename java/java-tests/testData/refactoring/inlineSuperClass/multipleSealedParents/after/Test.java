sealed interface SuperSuper permits Super, Test {}

class Super implements SuperSuper {}

non-sealed class Test implements SuperSuper {}