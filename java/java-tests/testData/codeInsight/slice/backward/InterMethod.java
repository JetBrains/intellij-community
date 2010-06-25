public class WW {
    void f(String ddd) {
        if (hashCode() == 0)
            ddd = <flown1111>"dd";
        foo(<flown111>ddd);
    }

    {
        f(<flown1112>"xxx");
    }

    {
        x(<flown1121>"zzz");
    }

    String x(String g) {
        String d = <flown1>foo(<flown112>g);
        return <caret>d;
    }

    private String foo(String i) {
        return <flown11>i;
    }
}
