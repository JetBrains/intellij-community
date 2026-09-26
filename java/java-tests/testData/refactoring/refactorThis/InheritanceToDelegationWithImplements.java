interface A { }

class B implements <caret>A {
    void test() {
      System.out.println();
    }
}