class Main {
  private static void m(Integer... arr) {}

  private static void m(int... arr) {}

  {
    m<error descr="Ambiguous method call: both 'Main.m(Integer...)' and 'Main.m(int...)' match">(1, 2)</error>;
  }
}