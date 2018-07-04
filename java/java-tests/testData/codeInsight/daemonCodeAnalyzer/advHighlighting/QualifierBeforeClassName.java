class E {
  class Outer {
    class S {
      public static final int SS = 0;
    }
  }

  Outer f() {
    int s = <error descr="Expected class or package">f()</error>.S.SS;
    int s1 = <error descr="Expected class or package">this</error>.Outer.S.SS;
    int s2 = Outer.S.SS;
    return null;
  }

  class opopo {
    public void main(String[] args) {
      String java = "bla";
      <error descr="Expected class or package">java.lang</error>.String.valueOf("");
    }
  }
}
