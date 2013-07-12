/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class TestIndex {

  public void statMethod1() {
    B.getB1().getC();
    B.getB1().getC();
    B.getB1().getC();
  }
  public void statMethod2() {
    B.getB2().getC();
    B.getB2().getC();
    B.getB2().getC();
  }
  public void statMethod3() {
    B.getB3().getC();
    B.getB3().getC();
    B.getB3().getC();
  }
  public void statMethod4() {
    B.getB4().getC();
    B.getB4().getC();
    B.getB4().getC();
  }
  public void statMethod5() {
    B.getB5().getC();
    B.getB5().getC();
    B.getB5().getC();
  }
  public void statMethod6() {
    B.getB6().getC();
    B.getB6().getC();
    B.getB6().getC();
  }
  public void statMethod7() {
    B.getB7().getC();
    B.getB7().getC();
    B.getB7().getC();
  }
  public void statMethod8() {
    B.getB8().getC();
    B.getB8().getC();
    B.getB8().getC();
  }
  public void statMethod9() {
    B.getB9().getC();
    B.getB9().getC();
    B.getB9().getC();
  }
  public void statMethod10() {
    B.getB10().getC();
    B.getB10().getC();
    B.getB10().getC();
  }
  public void statMethod11() {
    B.getB11().getC();
    B.getB11().getC();
    B.getB11().getC();
  }
}
class B {
  C getC() {
    return null;
  }

  static B b = new B();

  static B getB1() {
    return b;
  }
  static B getB2() {
    return b;
  }
  static B getB3() {
    return b;
  }
  static B getB4() {
    return b;
  }
  static B getB5() {
    return b;
  }
  static B getB6() {
    return b;
  }
  static B getB7() {
    return b;
  }
  static B getB8() {
    return b;
  }
  static B getB9() {
    return b;
  }
  static B getB10() {
    return b;
  }
  static B getB11() {
    return b;
  }
}

class C {}
