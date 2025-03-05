import java.util.List;

class A<T extends <error descr="'A.B' has private access in 'A'">A<T>.B</error>> {
    private class B {}
}

abstract class Outer implements List<<error descr="'Outer.Inner' has private access in 'Outer'">Outer.Inner</error>> {
  private static abstract class Inner implements List<Inner.Key> {
    private static final class Key {}
  }
}