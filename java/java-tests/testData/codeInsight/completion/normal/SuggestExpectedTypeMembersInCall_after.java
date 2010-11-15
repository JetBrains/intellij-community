class Super {
    public static final Super FOO = null;
}

class Intermediate {
    void foo(Super s, int a) {}
  void bar() {
    foo(Super.FOO<caret>)
  }
}


