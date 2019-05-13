
class Main {
  public static void var(int... <warning descr="Parameter 'x' is never used">x</warning>) {
    System.out.println("int... x");
  }

  public static void var(Object... <warning descr="Parameter 'x' is never used">x</warning>) {
    System.out.println("Object... x");
  }

  public static void var(Integer... <warning descr="Parameter 'x' is never used">x</warning>) {
    System.out.println("Integer... x");
  }

  public static void main(String[] args) {
    int i = 0;
    Integer i2 = 127;
    var<error descr="Ambiguous method call: both 'Main.var(int...)' and 'Main.var(Integer...)' match">(i, i2)</error>;
  }
}
