interface I {
    static <Z> void foo() { }
}

class A implements I {
    {
        System.out.println(A./*c1*/<error descr="Static method may only be called on its containing interface">foo</error>());
        Runnable r = A/*c2*/::<String><error descr="Static method may only be called on its containing interface">foo</error>;
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