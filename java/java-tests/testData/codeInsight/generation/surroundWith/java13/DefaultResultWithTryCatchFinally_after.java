class C {
    void test(int n) {
        String s = switch (n) {
            case 1 -> "";
            default -> {
                try {
                    yield "a" + "b";
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                }
            }
            ;
        };
    }
}