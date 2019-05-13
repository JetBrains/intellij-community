// "Convert to ThreadLocal" "true"
class X {
    private final ThreadLocal<String> s = ThreadLocal.withInitial(() -> "");
    private String t;
    private String u;
}