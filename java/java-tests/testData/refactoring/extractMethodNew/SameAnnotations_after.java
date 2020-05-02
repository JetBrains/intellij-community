class C {
    {
        newMethod();
    }

    void f() {
        newMethod();
    }

    private void newMethod() {
        @A int j = 0;
        System.out.println(j);
    }

    void g() {
        newMethod();
    }
}
@interface A {
    String value() default "asdf";
}