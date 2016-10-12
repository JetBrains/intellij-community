

class Unassigned {
  public final int a;
  public int b;

  Unassigned(int value) {
    b = <error descr="Variable '(this).a' might not have been initialized">(this).a</error>;
    a = value;
  }
}