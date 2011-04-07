// "Use existing implementation of 'm'" "true"
enum I {
    A("a") {
        void m() {
            <selection><caret>System.out.println("");</selection>
        }
    },
    B("b") {
        public void m() {
            System.out.println("");
        }
    };
    abstract void m();
    I(String s){}
}
