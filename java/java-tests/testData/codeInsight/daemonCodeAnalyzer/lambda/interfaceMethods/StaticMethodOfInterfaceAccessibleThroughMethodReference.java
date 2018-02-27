interface I {
    static <Z> void foo() { }
}

class A implements I {
    {
        System.out.println(A./*c1*/foo());
        Runnable r = A/*c2*/::<String>foo;
        System.out.println(r);
    }
}
