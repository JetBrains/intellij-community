class s {
    {
      x(<flown1121111>"xxx");
    }
    String x(String <flown112111>g) {
        String d = <flown1>foo(<flown11211>g);
        return <caret>d;
    }

    private String foo(String <flown1121>i) {
        return <flown11>i==null ? <flown111>null : <flown112>i;
    }
}