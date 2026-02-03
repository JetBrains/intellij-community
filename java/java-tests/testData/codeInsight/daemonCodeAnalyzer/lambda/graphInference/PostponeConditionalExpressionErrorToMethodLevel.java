class Test {
    
    void test(int num) {
        m<error descr="Expected 2 arguments but found 1">(num == 1 ? null : new Integer(1))</error>;
    }

    void m(String s, int i) {}
}
