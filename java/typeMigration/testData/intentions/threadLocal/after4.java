// "Convert to ThreadLocal" "true"
class Test {
    final ThreadLocal<String> field = ThreadLocal.withInitial(() -> "");
  void foo() {
    if (field.get().indexOf("a") == -1) return;
  }
}