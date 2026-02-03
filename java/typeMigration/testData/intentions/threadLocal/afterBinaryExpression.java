// "Convert to 'ThreadLocal'" "true"
class X {

    private static final ThreadLocal<Long> l = ThreadLocal.withInitial(() -> 1L + 2L);
}