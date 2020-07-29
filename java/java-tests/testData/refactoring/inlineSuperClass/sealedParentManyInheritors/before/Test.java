sealed class Super permits Test, Test1 {}

final class Test extends Super {}

final class Test1 extends Super {}