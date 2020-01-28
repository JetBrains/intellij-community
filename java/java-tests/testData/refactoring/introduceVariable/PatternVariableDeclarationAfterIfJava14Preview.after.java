class A {
    void test(Object obj) {
        if (!(obj instanceof String temp)) {
            return;
        }
        System.out.println(temp.trim());
        System.out.println(temp.length());
    }
}