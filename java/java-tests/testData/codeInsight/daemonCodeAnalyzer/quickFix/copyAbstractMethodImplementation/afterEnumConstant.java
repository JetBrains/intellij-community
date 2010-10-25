// "Use existing implementation of 'm'" "true"
enum I {
    A {
        void m() {
            <selection>System.out.println("");</selection>
        }
    },
    B {
        public void m() {
            System.out.println("");
        }
    };
    abstract void m();
}
