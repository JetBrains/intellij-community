class Test {
    void test(Object obj) {
        if (obj instanceof String && !((String) obj).isEmpty()) {
            System.out.println((<selection>(String) obj</selection>).trim());
        }
    }
}
