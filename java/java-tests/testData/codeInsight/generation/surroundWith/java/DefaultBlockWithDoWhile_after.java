class C {
    void test(int n) {
        String s = switch (n) {
            case 1 -> "";
            default -> {
                do {
                    break "a".substring(1);
                } while (true);
            };
        };
    }
}