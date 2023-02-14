class A {
    void test(Object obj) {
        if (obj instanceof String) {
            String s = (<selection>(String)obj</selection>).trim();
            System.out.println("Found");
        }
    }
}