class Tester {
    boolean a;

    native void doB();

    // IDEA-158665
    void inlinedMethod() {
        if (a)
            return;
        doB();
    }

    void useInlinedMethod() {
        <caret>inlinedMethod();
        System.out.println("ok");
    }
}