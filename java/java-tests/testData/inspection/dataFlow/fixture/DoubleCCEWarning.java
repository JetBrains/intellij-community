class X {
  public static void main(Object result) {
    final boolean varargs = result instanceof Intf;
    new Runnable() {
      public void run() { }
    };
    Intf a = (<warning descr="Casting 'result' to 'Intf' may produce 'java.lang.ClassCastException'">Intf</warning>)result;
  }

  public static void main2(Object result) {
    final boolean varargs = result instanceof Intf;
    if (!varargs) return;
    new Runnable() {
      public void run() { }
    };
    if (<warning descr="Condition 'result instanceof Intf' is always 'true'">result instanceof Intf</warning>) {
      System.out.println();
    }
  }
}
interface Intf {}