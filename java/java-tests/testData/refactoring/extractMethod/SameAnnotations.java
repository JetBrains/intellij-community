class C {
    {
        @A(value="asdf") int i = 0;
        System.out.println(i);
    }

    void f() {
        <selection>@A int j = 0;
        System.out.println(j);</selection>
    }

    void g() {
        @A("asdf") int k = 0;
        System.out.println(k);
    }
}
@interface A {
    String value() default "asdf";
}