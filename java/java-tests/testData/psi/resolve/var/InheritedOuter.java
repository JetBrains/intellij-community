class Outer {
  private String string;

  class Inner extends Outer {
    void test() {
      System.out.println(<ref>string); 
    }
  }
}