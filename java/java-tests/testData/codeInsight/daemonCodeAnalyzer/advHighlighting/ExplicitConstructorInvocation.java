class A {
    class S {
    }

    class C extends S {
        C(A c) {
            c.super();
        }
        C(B b) {
            b.super();
        }
    }
}
class B extends A {}

class A1 {
    static class S {
    }

    class C extends S {
        C(A1 c) {
            <error descr="Qualifier is not allowed because superclass 'A1.S' is not a non-static inner class">c</error>.super();
        }
    }
}

class C2 {
    C2(String c){
        <error descr="Qualifier is not allowed because superclass 'java.lang.Object' is not a non-static inner class">c</error>.super();
    }
}
class Scratch {
  void method() {
    class A {}

    class B extends A {
      B() {
        new Scratch().super();
      }
    }
  }
  static void method2() {
    class A {}

    class B extends A {
      B() {
        <error descr="Qualifier is not allowed because superclass 'A' is not a non-static inner class">new Scratch()</error>.super();
      }
    }
  }
}

class A3 {
    class S {
    }

    class C extends S {
        C(String c) {
          <error descr="Incompatible types. Found: 'java.lang.String', required: 'A3'">c</error>.super();
        }
    }
}