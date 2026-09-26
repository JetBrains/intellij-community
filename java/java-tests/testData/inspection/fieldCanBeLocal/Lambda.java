import java.util.*;

class Foo {
  private List<String> <warning descr="Field can be converted to a local variable">x</warning>;

  void test2() {
    x = new ArrayList<>();
    System.out.println(x);
    Runnable r = () -> {
      x = new ArrayList<>(); // could be local
      System.out.println(x);
    };
  }
}