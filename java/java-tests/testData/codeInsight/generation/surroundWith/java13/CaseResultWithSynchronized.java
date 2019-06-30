class C {
    void test(int n) {
        String s = switch (n) {
            case 1 -> /*0*/<selection>/*1*/"a" +/*2*/ "b"/*3*/;</selection>/*4*/
            default -> "";
        };
    }
}