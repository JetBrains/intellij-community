class A {
    {
        Object o;

        try {
            <selection>o = foo();</selection>
        }
        catch (Exception e) {
        }

        o.f();
    }
}