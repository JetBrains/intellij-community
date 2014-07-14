interface A {
    B getB();

    class B {
        public C c;

        private enum C {
            SOME
        }
    }
}

class D {
    public static void f(A a) {
        A.B b = a.getB();
        switch (<error descr="'A.B.C' is inaccessible here">b.c</error>) {
            case SOME:
                break;
        }
    }


}