// "Convert to ThreadLocal" "true"
class Test {
    final ThreadLocal<String[]> field = new ThreadLocal<String[]>() {
        @Override
        protected String[] initialValue() {
            return new String[]{};
        }
    };
}