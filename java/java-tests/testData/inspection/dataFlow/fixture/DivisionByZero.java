class Util {
  void goo(int a, int b) {
    try {
      a %= b;
      if (<warning descr="Condition 'b == 0' is always 'false'">b == 0</warning>)return;
    }
    catch (Exception e) {
      if (<warning descr="Condition 'e instanceof ArithmeticException' is always 'true'">e instanceof ArithmeticException</warning>){
        System.out.println("expected");
      }
    }
  }

}
