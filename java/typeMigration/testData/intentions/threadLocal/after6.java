// "Convert to ThreadLocal" "true"
class Test {
    static final ThreadLocal<Integer> field = new ThreadLocal<Integer>();
  static {
    field.set(new Integer(0));
  }
}