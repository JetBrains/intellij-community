// "Ex4" "true"

class Hello {
  public void g() throws <caret>Ex2 { }
  {
    try {
      g();
      g();
    } catch (Ex1 ex2) {
      ex2.printStackTrace();
    }
    try {
      g();
      g();
    } catch (Exception ex2) {
      ex2.printStackTrace();
    }
  }
}

class B {
  {
    try {
      new Hello().g();
      new Hello().g();
    } catch (Ex1 ex2) {
      ex2.printStackTrace();
    }
    try {
      new Hello().g();
      new Hello().g();
    } catch (Exception ex2) {
      ex2.printStackTrace();
    }
  }
}

class Ex1 extends Exception {}
class Ex2 extends Ex1 {}