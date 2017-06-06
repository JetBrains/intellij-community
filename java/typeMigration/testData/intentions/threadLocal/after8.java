// "Convert to ThreadLocal" "true"
class X {
    final ThreadLocal<Integer> i = ThreadLocal.withInitial(() -> 0);
}