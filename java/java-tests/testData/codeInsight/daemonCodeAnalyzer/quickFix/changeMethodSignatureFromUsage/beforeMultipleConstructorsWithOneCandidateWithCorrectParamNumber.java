// "<html> Change signature of X(<b>String</b>)</html>" "true-preview"
class X {
  X(Integer i) {}
  X() {}

  public static void main(String[] args) {
    new X("<caret>");
  }
}