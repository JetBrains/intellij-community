// "Use existing implementation of 'm'" "true"
enum I {
    A("a"),
    B("b") {
        public void m() {
            System.out.println("");
        }
    };
    abstract void <caret>m();
    I(String s){}
}
