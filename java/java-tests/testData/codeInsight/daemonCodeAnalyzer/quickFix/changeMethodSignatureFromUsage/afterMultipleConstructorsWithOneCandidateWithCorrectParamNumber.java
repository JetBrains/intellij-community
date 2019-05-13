// "<html> Change signature of X(<b>String</b>)</html>" "true"
class X {
  X(Integer i) {}
  X(String s) {}

  public static void main(String[] args) {
    new X("");
  }
}