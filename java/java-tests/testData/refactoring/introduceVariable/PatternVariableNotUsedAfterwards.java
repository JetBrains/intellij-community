class A {
    void test(Object obj) {
        if (<selection>obj instanceof String s && s.trim().isEmpty()</selection>) {
            System.out.println("Found");
        }
    }
}