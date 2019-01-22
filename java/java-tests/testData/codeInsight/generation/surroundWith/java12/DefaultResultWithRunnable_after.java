class C {
    void test(int n) {
        String s = switch (n) {
            case 1 -> "";
            default -> /*0*/{
                Runnable runnable = new Runnable() {
                    public void run() {
                        /*1*/
                        "a" + "b"/*2*/;
                    }
                };
            }/*3*/
        };
    }
}