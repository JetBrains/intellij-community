public class NonDisjunctTypes {
  static class Ex1 extends Exception {}
  static class Ex2 extends Ex1 {}
  static class Ex3 extends Ex1 {}

  public void test() {
    try {
      if(Math.random() > 0.5) {
        throw new Ex1();
      }
      if(Math.random() > 0.5) {
        throw new Ex2();
      }
      if(Math.random() > 0.5) {
        throw new Ex3();
      }
    }
    catch(RuntimeException | Ex2 | Ex3 ex) {
      ex.printStackTrace();
    }
    <warning descr="'catch' branch identical to 'RuntimeException | Ex2 | Ex3' branch">catch <caret>(Ex1 ex)</warning> {
      ex.printStackTrace();
    }
  }
}