class A {
    void test(Object obj) {
        if (obj instanceof String temp) {
            String s = temp.trim();
            System.out.println("Found");
        }
    }
}