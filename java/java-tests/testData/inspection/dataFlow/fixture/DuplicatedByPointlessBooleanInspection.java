// IDEA-304296
class BooleanOrEquals {
  void foo(boolean a, boolean b) {
    boolean c = !(b && false);
    boolean d = a ^ b ^ true;
    boolean x = a ^ !true ^ b;

    boolean y = false || <warning descr="Condition 'c' is always 'true' when reached">c</warning>;
    boolean z = b != true;
  }

  static int i = 1;
  public static void main(String[] args) {
    boolean b = false;
    if (i == 1 && (b |= true))
    System.out.println("i == 1");
    if (i == 1 && (<warning descr="Condition 'b' at the left side of assignment expression is always 'true'. Can be simplified">b</warning> |= false))
    System.out.println("i == 1");
    if (<warning descr="Variable update does nothing">b</warning> |= false)
    System.out.println("i == 1");
    if (b |= true)
    System.out.println("i == 1");
    if (<warning descr="Variable is already assigned to this value">b</warning> = true)
      System.out.println("i == 1");
    System.out.println(b);

    if (i == 1 && (<warning descr="Condition 'b' at the left side of assignment expression is always 'true'. Can be simplified">b</warning> &= true)) { }
    if (<warning descr="Condition 'i == 1 && (b &= false)' is always 'false'">i == 1 && (<warning descr="Condition 'b' at the left side of assignment expression is always 'true'. Can be simplified">b</warning> &= false)</warning>) { }
    if (<warning descr="Variable update does nothing">b</warning> &= true) {}
    if (b &= false) {}
  }

}