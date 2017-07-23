
class A1 extends Exception {}

class A2 extends Exception {}

class BA1 extends A1 {}

class ErrorTest {
  public void testNok(boolean mode) throws BA1, A2 {
    try {
      if (mode) {
        throw new BA1();
      } else {
        throw new A2();
      }
    } catch (A1 ex) {
      throw ex;
    }
  }
}