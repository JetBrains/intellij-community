// "Convert to ThreadLocal" "true"
class Test {
    final ThreadLocal<Integer> field = ThreadLocal.withInitial(() -> 0);
}