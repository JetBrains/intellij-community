import java.util.*;

class Plain { }

class Outer<T> {
  class Inner { }
  static class Nested { }
  static class GenericNested<U> { }
}

abstract class AbstractGeneric<T> { }

interface GenericInterface<T> { }

enum Color { RED }

class InvalidDiamondUsages {
  <T> void test(Outer<String> outer) {
    Plain p = new Plain<error descr="Diamond operator is not applicable to non-parameterized types"><></error>();
    Object o = new Plain<error descr="Diamond operator is not applicable to non-parameterized types"><></error>() { };
    Object s = new String<error descr="Diamond operator is not applicable to non-parameterized types"><></error>("a");
    Outer<String>.Inner in = outer.new Inner<error descr="Diamond operator is not applicable to non-parameterized types"><></error>();
    Outer.Nested nested = new Outer.Nested<error descr="Diamond operator is not applicable to non-parameterized types"><></error>();
    AbstractGeneric<String> ag = <error descr="'AbstractGeneric' is abstract; cannot be instantiated">new AbstractGeneric<>()</error>;
    GenericInterface<String> gi = <error descr="'GenericInterface' is abstract; cannot be instantiated">new GenericInterface<>()</error>;
    List<String> list = <error descr="'List' is abstract; cannot be instantiated">new List<>()</error>;
    Object e = new Color<error descr="Diamond operator is not applicable to non-parameterized types"><></error>();
    Object t = new T<error descr="Diamond operator is not applicable to non-parameterized types"><></error>();
  }
}
