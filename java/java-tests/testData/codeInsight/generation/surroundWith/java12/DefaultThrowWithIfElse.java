class C {
    void test(int n) {
        String s = switch (n) {
            case 1 -> "";
            default -> <selection>throw new RuntimeException("a");</selection>
        };
    }
}