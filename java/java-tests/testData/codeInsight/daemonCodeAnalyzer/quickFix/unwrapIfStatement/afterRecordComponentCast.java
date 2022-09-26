// "Unwrap 'if' statement extracting side effects" "true"
class Test {
  void foo(Object obj) {
    if (!(obj instanceof Rec(A a2))) return;
      Rec rec = (Rec) obj;
      A a = (A) rec.i();
      a.doA();
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