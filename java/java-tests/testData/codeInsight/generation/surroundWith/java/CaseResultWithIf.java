class C {
    void test(int n) {
        String s = switch (n) {
            case 1 -> <selection>"a".substring(1);</selection>
            default -> "";
        };
    }
}