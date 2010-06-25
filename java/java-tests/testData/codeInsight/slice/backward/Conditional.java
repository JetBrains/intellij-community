class s {
    {
      x(<flown11211>"xxx");
    }
    String x(String g) {
        String d = <flown1>foo(<flown1121>g);
        return <caret>d;
    }

    private String foo(String i) {
        return <flown11>i==null ? <flown111>null : <flown112>i;
    }
}