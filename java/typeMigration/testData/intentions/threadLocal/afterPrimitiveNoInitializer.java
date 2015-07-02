// "Convert to ThreadLocal" "true"
class Test {
    final ThreadLocal<Integer> field = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };
}