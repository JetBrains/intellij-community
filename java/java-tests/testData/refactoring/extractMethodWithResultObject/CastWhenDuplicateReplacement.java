class Test {

    void foo(Object x) {
        if (x instanceof String) x = ((String)x).substring(1);
        if (x instanceof String) x = <selection>((String)x).substring(1)</selection>;
    }
}