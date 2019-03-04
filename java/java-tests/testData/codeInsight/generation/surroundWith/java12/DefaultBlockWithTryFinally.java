class C {
    void test(int n) {
        String s = switch (n) {
            case 1 -> "";
            default -> /*0*/<selection>{/*1*/break /*2*/"a";/*3*/}</selection>/*4*/
        };
    }
}