// "Convert to 'ThreadLocal'" "true"
class T {
  private static final long <caret>l = -1; // choose "Convert to ThreadLocal" intention

  static {
    int i = 1;
    l = i + 5;
    long z = l;
  }
}