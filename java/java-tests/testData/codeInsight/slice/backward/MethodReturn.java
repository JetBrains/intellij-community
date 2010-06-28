
class s {
    {
      x(<flown1211>"xxx");
    }
    String x(String g) {
        String d = <flown1>foo(<flown121>g);
        return <caret>d;
    }

    private String foo(String i) {
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
