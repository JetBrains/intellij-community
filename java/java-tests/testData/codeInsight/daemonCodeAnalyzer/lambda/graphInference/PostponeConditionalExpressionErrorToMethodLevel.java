class Test {
    
    void test(int num) {
        m<error descr="'m(java.lang.String, int)' in 'Test' cannot be applied to '(java.lang.Integer)'">(num == 1 ? null : new Integer(1))</error>;
    }

    void m(String s, int i) {}
}
