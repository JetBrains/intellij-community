class C implements A {
    C(A a) {}
}

interface A {
    A a = new C(<error descr="'A.this' cannot be referenced from a static context">this</error>);
    A a1 = new C(<error descr="Cannot find symbol variable this">this</error>){};

    class B  {
        A foo() {
            return <error descr="'A.this' cannot be referenced from a static context">A.this</error>;
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