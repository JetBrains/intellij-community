// "Convert to ThreadLocal" "true"
class Test {
    final ThreadLocal<Integer> field = ThreadLocal.withInitial(() -> new Integer(0));
  void foo() {
    if (field.get() == null) return;
  }
}