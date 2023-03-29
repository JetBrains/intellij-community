public class TestChain {
  private String x = "2";

  public TestChain do1() {
    return this;
  }

  public TestChain do2() {
    x = "1";
    return this;
  }

  public static void main(String[] args) {
    TestChain myClass = new TestChain();
    myClass.do1().do2();
    System.out.println(myClass.x);
  }
}
