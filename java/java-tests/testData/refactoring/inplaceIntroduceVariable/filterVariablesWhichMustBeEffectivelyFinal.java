class Test {
    void simpleMethod() {
        int x = 0;
        int y = 0;
        Runnable t = () -> System.out.println(x);
        int k = <caret>1;
    }
}
