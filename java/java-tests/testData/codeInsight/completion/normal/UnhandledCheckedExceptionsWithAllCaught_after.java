class X{
  class CheckedException1 extends Exception {
  }
  class CheckedException2 extends Exception {
  }
  public void test() {
    try {
      method1();
      throw new CheckedException2();
    } catch(CheckedException1 e) {
    } catch(CheckedException2 e) {
    } catch (Exception e) {
        <selection>throw new RuntimeException(e);</selection><caret>
    }
  }

  private void method1() throws CheckedException1{

  }
}
