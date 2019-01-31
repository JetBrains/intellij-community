class A {
    void foo() throws Exception {}
}

class B extends A {
    void foo()  {

    }
}

class C {
    {
        A a = new B();
        ((B) a).foo();
    }
}