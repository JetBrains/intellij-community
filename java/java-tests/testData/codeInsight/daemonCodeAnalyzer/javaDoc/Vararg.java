public class Test {
   /**
     * @see Test#test(String, int...)
     * @see Test#<error>test(String, long...)</error>
   **/
    void foo() {}

    void test (String u, int ... i) {}
}
