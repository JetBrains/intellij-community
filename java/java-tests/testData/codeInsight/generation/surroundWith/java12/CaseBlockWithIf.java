class C {
    void test(int n) {
        String s = switch (n) {
            case 1 -> <selection>{ break "a"; }</selection>
            default -> "";
        };
    }
}