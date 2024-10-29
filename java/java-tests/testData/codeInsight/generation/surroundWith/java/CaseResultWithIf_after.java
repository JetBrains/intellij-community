class C {
    void test(int n) {
        String s = switch (n) {
            case 1 -> {
                if () {
                    yield "a".substring(1);
                }
            }
            default -> "";
        };
    }
}