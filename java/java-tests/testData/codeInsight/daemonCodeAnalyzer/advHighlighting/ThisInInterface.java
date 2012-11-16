class C implements A {
    C(A a) {}
}

interface A {
    A a = new C(<error descr="Cannot find symbol variable this">this</error>);
    A a1 = new C(<error descr="Cannot find symbol variable this">this</error>){};
}