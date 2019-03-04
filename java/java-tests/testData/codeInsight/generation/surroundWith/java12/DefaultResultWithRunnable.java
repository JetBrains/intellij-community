class C {
    void test(int n) {
        String s = switch (n) {
            case 1 -> "";
            default -> /*0*/<selection>/*1*/"a" + "b"/*2*/;</selection>/*3*/
        };
    }
}