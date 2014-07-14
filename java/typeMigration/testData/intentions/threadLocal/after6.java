// "Convert to ThreadLocal" "true"
class Test {
  static final ThreadLocal<Integer> field;
  static {
    field = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return new Integer(0);
        }
    };
  }
}