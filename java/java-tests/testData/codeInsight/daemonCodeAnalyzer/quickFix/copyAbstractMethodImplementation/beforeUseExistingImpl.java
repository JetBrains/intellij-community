// "Use existing implementation of 'm'" "true"
interface SampleInterface {
  /**
   * Specified in the interface
   */
  public void m<caret>();
}

class Test3 implements SampleInterface {
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
