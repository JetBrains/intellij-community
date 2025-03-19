class C {
    void test(int n) {
        String s = switch (n) {
            case 1 -> "";
            default -> /*0*/{
                try {
                    /*1*/
                    yield /*2*/"a";/*3*/
                } finally {
                    <caret>
                }
            }/*4*/
        };
    }
}