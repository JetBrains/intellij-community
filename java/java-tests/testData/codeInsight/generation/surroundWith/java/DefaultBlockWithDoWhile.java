class C {
    void test(int n) {
        String s = switch (n) {
            case 1 -> "";
            default -> <selection>{ break "a".substring(1); }</selection>;
        };
    }
}