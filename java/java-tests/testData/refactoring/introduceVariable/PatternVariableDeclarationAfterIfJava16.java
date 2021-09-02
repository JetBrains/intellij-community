class A {
    void test(Object obj) {
        if (!(obj instanceof String)) {
            return;
        }
        System.out.println(<selection>((String)obj)</selection>.trim());
        System.out.println(((String)obj).length());
    }
}