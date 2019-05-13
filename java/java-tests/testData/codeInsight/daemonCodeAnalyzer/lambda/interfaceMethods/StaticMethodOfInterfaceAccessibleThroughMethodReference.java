interface I {
    static <Z> void foo() { }
}

class A implements I {
    {
        System.out.println(A./*c1*/<error descr="Static method may be invoked on containing interface class only">foo</error>());
        Runnable r = <error descr="Static method may be invoked on containing interface class only">A/*c2*/::<String>foo</error>;
        System.out.println(r);
    }
}

class B {
    static void foo() {}
}

class C extends B {
    {
        Runnable r = C::foo;
    }
}