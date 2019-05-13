
class Test {
  public void test(String... <warning descr="Parameter 'a' is never used">a</warning>){}

  public void test(int... <warning descr="Parameter 'a' is never used">a</warning>){}

  public static void main(String[] args) {
    new Test().test<error descr="Ambiguous method call: both 'Test.test(String...)' and 'Test.test(int...)' match">()</error>;
  }
}