final class Test extends Super {}

non-sealed class Test1 extends Super {}

sealed class Test2 extends Super permits Test3 {}

final class Test3 extends Test2 {}

