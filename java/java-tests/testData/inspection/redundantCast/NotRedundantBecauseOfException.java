class A {
    void foo() throws Exception {}
}

class B extends A {
    void foo()  {

    }
}

class C {
    {
        B b = new B();
        try {
          ((A)b).foo();
        } catch (Exception e) {}
    }
}