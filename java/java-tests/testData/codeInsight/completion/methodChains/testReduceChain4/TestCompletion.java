import java.jang.String;

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

public class TestCompletion {

  public void method() {
    C c = <caret>
  }

}
