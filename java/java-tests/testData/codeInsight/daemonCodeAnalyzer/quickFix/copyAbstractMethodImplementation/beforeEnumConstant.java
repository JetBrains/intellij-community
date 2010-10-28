// "Use existing implementation of 'm'" "true"
enum I {
    A,
    B {
        public void m() {
            System.out.println("");
        }
    };
    abstract void <caret>m();
}
