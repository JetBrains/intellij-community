// "Ex4" "true"

class Hello {
  public void g() { }
  {
      g();
      g();
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
      new Hello().g();
      new Hello().g();
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