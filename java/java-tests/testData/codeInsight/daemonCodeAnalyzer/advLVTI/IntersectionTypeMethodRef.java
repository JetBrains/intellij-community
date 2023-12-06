import java.util.*;

class Main {
  enum Enum {}

  @FunctionalInterface
  interface EnumConsumer {
    void accept(Enum e);
  }

  public static void main(String[] args) {
    var set = Math.random() > 0.5d ? new HashSet<Enum>() : EnumSet.noneOf(Enum.class);
    EnumConsumer consumer = set::add;
  }
}
class X {
  interface A {
    void add(int x);
  }
  interface B {
    void add(String s);
  }
  interface StringConsumer {
    void accept(String s);
  }

  <T extends A & B> void x(T t) {
    StringConsumer cons1 = s -> t.add(s);
    StringConsumer cons2 = t::add;
    var i = (A & B)t;
    StringConsumer cons3 = s -> i.add(s);
    StringConsumer cons4 = i::add; // good code red
  }
}