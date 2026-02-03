// "Convert to 'ThreadLocal'" "true"
class X {
  public static final boolean <caret>B = true;

  public static void main(String[] args) {
    System.out.println(!B);
  }
}