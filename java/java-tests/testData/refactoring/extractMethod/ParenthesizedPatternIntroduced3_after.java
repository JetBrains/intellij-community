class X {
    void test(Object obj) {
        if (newMethod(obj)) {
            System.out.println("found");
        }
    }

    private boolean newMethod(Object obj) {
        return obj instanceof (String s) && s.length() > 5;
    }
}