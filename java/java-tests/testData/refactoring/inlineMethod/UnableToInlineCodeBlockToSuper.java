
class Main {
  public Main() {
    this(f<caret>oo());
  }

  public Main(boolean b) { }

  private static boolean foo() {
    if (2 < 1) {
      return true;
    } else {
      return false;
    }
  }
}