// "Convert to ThreadLocal" "true"
class X {
    private final ThreadLocal<String> s = new ThreadLocal<String>() {
        @Override
        protected String initialValue() {
            return "";
        }
    };
    private String t;
    private String u;
}