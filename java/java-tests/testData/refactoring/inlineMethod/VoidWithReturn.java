class Test {
    void method() {
        otherMethod();
        System.out.println("Here");
    }
    void otherMethod<caret>() {
        return;
    }
}