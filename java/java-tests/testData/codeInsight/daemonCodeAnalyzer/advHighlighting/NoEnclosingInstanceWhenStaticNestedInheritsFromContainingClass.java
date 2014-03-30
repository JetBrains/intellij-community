class Test {
    class Test1 extends Test {}
    static class Test2 extends <error descr="No enclosing instance of type 'Test' is in scope">Test1</error> {}


    static class Test11 extends Test {}
    static class Test21 extends Test11 {}
}