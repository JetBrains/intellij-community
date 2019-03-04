class C {
    void test(int n) {
        String s = switch (n) {
            case 1 -> "";
            default -> {
                try {
                    break "a" + "b";
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                }
            }
            ;
        };
    }
}