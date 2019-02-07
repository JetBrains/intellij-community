class C {
    void test(int n) {
        String s = switch (n) {
            case 1 -> /*0*/<selection>/*1*/throw new /*2*/RuntimeException("a")/*3*/;</selection>/*4*/
            default -> "";
        };
    }
}