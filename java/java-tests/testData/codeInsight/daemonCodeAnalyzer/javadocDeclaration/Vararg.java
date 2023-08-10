class Test {
   /**
     * @see Test#test(String, int...)
     * @see Test#<error descr="Cannot resolve symbol 'test(String, long...)'">test</error>(String, long...)
   **/
    void foo() {}

    void test (String u, int ... i) {}
}
