// "Convert to ThreadLocal" "true"
class Test {
    final ThreadLocal<String[]> field = ThreadLocal.withInitial(() -> new String[]{});
}