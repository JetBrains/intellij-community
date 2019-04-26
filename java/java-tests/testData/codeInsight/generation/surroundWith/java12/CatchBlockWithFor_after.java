class C {
    void test(int n) {
        String s = switch (n) {
            case 1 -> {
                for () {
                    break "a";
                }
            }
            default -> "";
        };
    }
}