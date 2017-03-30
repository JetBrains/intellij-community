class WW {
    void f(String ddd) {
        if (hashCode() == 0)
            ddd = "dd";
        foo(ddd);
    }

    {
        f("xxx");
    }

    {
        x(<flown111111>"zzz");
    }

    String x(String <flown11111>g) {
        String d = <flown1>foo(<flown1111>g);
        return <caret>d;
    }

    private String foo(String <flown111>i) {
        return <flown11>i;
    }
}
