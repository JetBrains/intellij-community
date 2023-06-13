// "Convert to 'ThreadLocal'" "true"
class T {
  private static final long <caret>l = -1;

  static {
    int i = 1;
    l = i + 5;
    long z = l;
  }
}