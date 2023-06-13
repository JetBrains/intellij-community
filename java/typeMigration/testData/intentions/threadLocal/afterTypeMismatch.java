// "Convert to 'ThreadLocal'" "true"
class T {
    private static final ThreadLocal<Long> l = ThreadLocal.withInitial(() -> -1L); // choose "Convert to ThreadLocal" intention

  static {
    int i = 1;
    l.set((long) (i + 5));
    long z = l.get();
  }
}