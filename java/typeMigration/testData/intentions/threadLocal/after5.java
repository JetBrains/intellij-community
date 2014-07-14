// "Convert to ThreadLocal" "true"
class Test {
    final ThreadLocal<Integer> field = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return new Integer(0);
        }
    };
  void foo(Test t) {
    if (t.field.get() == null) return;
  }
}