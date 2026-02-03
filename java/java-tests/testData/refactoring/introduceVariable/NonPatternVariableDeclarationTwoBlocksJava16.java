class A {
    private void test(Object obj) {
        if (obj instanceof String) {
            System.out.println(((String) obj).trim());
            System.out.println(((String) obj).trim());
            System.out.println(<selection>((String) obj)</selection>.trim());
        }
        if (obj instanceof String) {
            System.out.println(((String) obj).trim());
            System.out.println(((String) obj).trim());
            System.out.println(((String) obj).trim());
        }
    }
}