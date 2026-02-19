// "Use existing implementation of 'm'" "true"
interface SampleInterface {
  /**
   * Specified in the interface
   */
  public void m();
}

class Test3 implements SampleInterface {
    public void m() {
      System.out.println("Test1 implementation");
    }
}

class Test1 implements SampleInterface {
  /**
   * Test1 implementation
   */
  @Override
  public void m() {
    System.out.println("Test1 implementation");
  }
}
