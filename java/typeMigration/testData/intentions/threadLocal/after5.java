// "Convert to ThreadLocal" "true"
class Test {
    final ThreadLocal<Integer> field = ThreadLocal.withInitial(() -> new Integer(0));
  void foo(Test t) {
    if (t.field.get() == null) return;
  }
}