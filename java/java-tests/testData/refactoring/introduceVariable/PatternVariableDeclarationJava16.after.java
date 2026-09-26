class A {
    void test(Object obj) {
        if (obj instanceof String temp && temp.trim().isEmpty()) {
            System.out.println("Found");
        }
    }
}