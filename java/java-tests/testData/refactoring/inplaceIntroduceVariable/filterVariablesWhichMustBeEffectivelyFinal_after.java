class Test {
    void simpleMethod() {
        int x = 0;
        int y = 0;
        Runnable t = () -> System.out.println(x);
        y = 1;
        int k = y;
    }
}
