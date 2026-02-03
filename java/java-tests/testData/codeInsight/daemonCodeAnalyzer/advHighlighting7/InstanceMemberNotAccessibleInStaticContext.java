class Foo {
    public void foo() {}

    public static void foo(String... s){}
}

class A {
    {
        Foo.<error descr="Non-static method 'foo()' cannot be referenced from a static context">foo</error>();
    }
}
