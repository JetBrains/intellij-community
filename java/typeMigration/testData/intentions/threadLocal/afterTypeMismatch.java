// "Convert to 'ThreadLocal'" "true"
class T {
    private static final ThreadLocal<Long> l = ThreadLocal.withInitial(() -> (long) 1); // choose "Convert to ThreadLocal" intention
}