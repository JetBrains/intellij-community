class C {
    void test(int n) {
        String s = switch (n) {
            case 1 -> "";
            default -> {
                if () {
                    throw new RuntimeException("a");
                } else {
                }
            }
        };
    }
}