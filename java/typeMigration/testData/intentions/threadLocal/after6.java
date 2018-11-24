// "Convert to ThreadLocal" "true"
class Test {
  static final ThreadLocal<Integer> field;
  static {
    field = ThreadLocal.withInitial(() -> new Integer(0));
  }
}