class Test {
    void test() {
        while (true) {
            boolean temp = foo();
            if (!temp) break;
        }
    }
    
    native boolean foo();
}