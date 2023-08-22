import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DoubleBraceInitialization {

  void foo() {
    final HashMap map = new <warning descr="Double brace initialization">HashMap</warning>() {{
      // comment
      put("a", "b");
      put("a", "b");
      put("a", "b");
      put("a", "b");
    }};
  }

  static final List<Integer> list = new <warning descr="Double brace initialization">ArrayList<Integer></warning>() {{
    for (int i = 0; i < 10; i++) {
      add(i);
    }
  }};

  void m(A a) {}
  void n() {
    m(new <warning descr="Double brace initialization">A</warning>() {{ setI(1); setJ(2); }});
  }

  class A {
    void setI(int i) {}
    void setJ(int j) {}
  }
}