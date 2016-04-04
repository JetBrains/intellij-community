class Test {
  public void test(A test) {
    if (test instanceof B) {
      String s = <caret>
    }
  }
}

interface A {
  String test();
}

class B implements A {
  public String test() {
    return "test";
  }
}
