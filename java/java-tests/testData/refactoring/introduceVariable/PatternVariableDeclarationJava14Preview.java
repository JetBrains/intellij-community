class A {
    void test(Object obj) {
        if (obj instanceof String && <selection>((String)obj)</selection>.trim().isEmpty()) {
            System.out.println("Found");
        }
    }
}