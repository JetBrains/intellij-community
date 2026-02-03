// "Convert to 'ThreadLocal'" "true"
class T {
    private static final ThreadLocal<Long> l = new ThreadLocal<Long>() {
        @Override
        protected Long initialValue() {
            return -1L;
        }
    };

  static {
    int i = 1;
    l.set((long) (i + 5));
    long z = l.get();
  }
}