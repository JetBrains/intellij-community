class X {

    public static final Object xxx = new Object();

    void x() {
        new Object() {
            void x() {
                System.out.println(xxx);
            }
        };
    }
}