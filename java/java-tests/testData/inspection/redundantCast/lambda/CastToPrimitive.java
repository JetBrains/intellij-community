
import java.util.function.Consumer;

class Program {
  static <T> T foo(Consumer<T> c) {
    c.accept(null);
    return null;
  }

  public static void main(String[] args) {
    long l = (<warning descr="Casting 'foo(...)' to 'long' is redundant">long</warning>)foo(x -> bar(x));
  }

  static void bar(Long x) { System.out.println(1);}
  static void bar(Object x) { System.out.println(2); }
}