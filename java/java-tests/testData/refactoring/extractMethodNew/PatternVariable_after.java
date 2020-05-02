class X {
    void test(Object obj) {
        if (obj instanceof String a) {
            System.out.println(newMethod(a));
        }
    }

    private int newMethod(String a) {
        return a.length();
    }
}