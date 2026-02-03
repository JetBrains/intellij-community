// "Unwrap 'if' statement extracting side effects" "true"
class Test {
  void foo(Object obj) {
    if (!(obj instanceof Rec(A a2))) return;
    if (<caret>obj instanceof Rec(A a)) {
      a.doA();
    }
  }
}

record Rec(I i) {
}

interface I {
}

class A implements I {
  void doA() {
  }
}

class B implements I {
  void doB() {
  }
}