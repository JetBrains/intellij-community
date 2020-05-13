class A {
    void test(Object obj) {
        boolean temp = obj instanceof String s && s.trim().isEmpty();
        if (temp) {
            System.out.println("Found");
        }
    }
}