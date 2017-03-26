class Main {
  void foo() {
    Class<?> a,b,c,d;
    d = Class.forName("Test");
    a = d;
    b = a;
    c = b;
    d = c;
    d.getField("<caret>");
  }
}

class Test {
  public int num;
  public int num2;
}
