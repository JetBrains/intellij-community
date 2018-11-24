// "Convert to ThreadLocal" "true"
class Test {
    final ThreadLocal<Integer> field = ThreadLocal.withInitial(() -> 0);
  void foo() {
    field.set(field.get() + 1);
  }
}