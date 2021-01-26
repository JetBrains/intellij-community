class A {
    void test(String obj) {
        if (obj instanceof String && <selection>((String)obj)</selection>.trim().isEmpty()) {
            System.out.println("Found");
        }
    }
}