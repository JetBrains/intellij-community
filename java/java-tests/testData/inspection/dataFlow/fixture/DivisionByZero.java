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

  public static void main(String[] args, int d) {
    String is = null;
    if (d != 0) return;

    try {
      if (Math.random() > 0.5) {
        double k = 1 / <warning descr="Value 'd' is always '0'">d</warning>;
      } else {
        is = "This is printed half of the time";
        double k = 1 / 0;
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      if (is != null) {
        System.out.println(is);
      }
    }
  }

}
