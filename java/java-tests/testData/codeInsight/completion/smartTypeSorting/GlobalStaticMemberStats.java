import java.util.Set;

public class Foo {

  void foo() {
    Set s = newLi<caret>
  }
}

class Bar {
  static Set newLinkedSet0() {}
  static Set newLinkedSet1(int a) {}
  static Set newLinkedSet2(int a, int b) {}
}