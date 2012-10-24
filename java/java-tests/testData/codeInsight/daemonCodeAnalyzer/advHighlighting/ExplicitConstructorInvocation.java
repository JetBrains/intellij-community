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
            <error descr="'A1.S' is not an inner class">c</error>.super();
        }
    }
}

class C2 {
    C2(String c){
        <error descr="'java.lang.Object' is not an inner class">c</error>.super();
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