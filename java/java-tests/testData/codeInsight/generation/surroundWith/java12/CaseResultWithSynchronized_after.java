class C {
    void test(int n) {
        String s = switch (n) {
            case 1 -> /*0*/{
                synchronized () {
                    /*1*/
                    "a" +/*2*/ "b"/*3*/;
                }
            }/*4*/
            default -> "";
        };
    }
}