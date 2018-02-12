public class IDEA168768 {
  private final boolean b = false;
  private boolean b2 = false;

  public void m() throws Exception {
    final long s;
    if (b) {
      s = 1;
    }
    if (b) {
      System.out.println(s);
    }
  }

  public void m1() throws Exception {
    final long s;
    final boolean b1 = false;
    if (b1) {
      s = 1;
    }
    if (b1) {
      System.out.println(s);
    }
  }

  public void m2() throws Exception {
    final long s;
    if (b2) {
      s = 1;
    }
    if (b2) {
      System.out.println(<error descr="Variable 's' might not have been initialized">s</error>);
    }
  }
}