class WW {
    void f(String <flown11111>ddd) {
        if (hashCode() == 0)
            ddd = <flown11112>"dd";
        foo(<flown1111>ddd);
    }

    {
        f(<flown111111>"xxx");
    }

    {
        x(<flown111211>"zzz");
    }

    String x(String <flown11121>g) {
        String d = <flown1>foo(<flown1112>g);
        return <caret>d;
    }

    private String foo(String <flown111>i) {
        return <flown11>i;
    }
}
