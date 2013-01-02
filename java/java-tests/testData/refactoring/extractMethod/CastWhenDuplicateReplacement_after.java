class Test {

    void foo(Object x) {
        if (x instanceof String) x = newMethod((String) x);
        if (x instanceof String) x = newMethod((String) x);
    }

    private String newMethod(String x) {
        return ((String)x).substring(1);
    }
}