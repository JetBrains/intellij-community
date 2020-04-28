class Test {
    void test(int x, float y){
        <selection>int code;
        if (x == 22) return;
        if (x > 0) {
            code = 1;
        } else {
            code = 42;
        }</selection>
        System.out.println(code);
    }
}