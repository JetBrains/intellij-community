// "Convert to ThreadLocal" "true"
class Test {
    final ThreadLocal<String> field = ThreadLocal.withInitial(() -> "");
  void foo() {
    System.out.println(field.get());
  }
}