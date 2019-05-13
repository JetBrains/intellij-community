class s {
    {
      x(<flown121111>"xxx");
    }
    String x(String <flown12111>g) {
        String d = <flown1>foo(<flown1211>g);
        return <caret>d;
    }

    private String foo(String <flown121>i) {
        if (i==null) {
            new Callable<String>(){
                public String call() throws Exception {
                    return "xxxxxxxxx";
                }
            };
            return <flown11>null;
        } else {
            return <flown12>i;
        }
    }
}

interface Callable<V> {
    V call() throws Exception;
}
