import java.util.*;

class C {
  interface Simplest {
    void m();
  }
  void use(Simplest s) { }

  interface IntParser {
    int parse(String s);
  }

  void test() {
    Simplest simplest = () -> { };
    use(() -> { });

    IntParser intParser = (String s) -> Integer.parseInt(s);
  }

  Runnable foo() {
    return () -> { System.out.println("foo"); };
  }
}