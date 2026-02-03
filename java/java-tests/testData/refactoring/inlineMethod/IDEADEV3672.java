class Tester1 {
    void caller() {
        <caret>method(); 
    }
    void method() {
        new Runnable() {
            public void run() {
                other();
            }
        };
    }
    void other() { }
}