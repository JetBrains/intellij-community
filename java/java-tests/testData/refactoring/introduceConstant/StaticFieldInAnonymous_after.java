class X {

    void x() {
        new Object() {
            public static final Object xxx = new Object();

            void x() {
                System.out.println(xxx);
            }
        };
    }
}