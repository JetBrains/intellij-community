class C implements A {
    C(A a) {}
}

interface A {
    A a = new C(<error descr="Cannot find symbol variable this">this</error>);
    A a1 = new C(<error descr="Cannot find symbol variable this">this</error>){};

    class B  {
        A foo() {
            return <error descr="Cannot find symbol variable this">A.this</error>;
        }

        B foo1() {
            return B.this;
        }

        class D {
            B f() {
                return B.this;
            }
        }
    }
}