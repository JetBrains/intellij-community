class Super {
    public static final Super FOO = null;
}
class Super2 {
}

class Intermediate {
    void foo(Super s, int a) {}
  void bar() {
    foo(Super.<caret>)
  }
}


