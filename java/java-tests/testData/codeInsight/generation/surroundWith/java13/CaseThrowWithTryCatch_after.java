class C {
    void test(int n) {
        String s = switch (n) {
            case 1 -> /*0*/{
                try {
                    /*1*/
                    throw new /*2*/RuntimeException("a")/*3*/;/*4*/
                } catch (RuntimeException e) {
                    throw new RuntimeException(e);
                }
            }
            default -> "";
        };
    }
}