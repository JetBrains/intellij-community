// "Convert to ThreadLocal" "true"
class X {
    private static final ThreadLocal<Integer> count = ThreadLocal.withInitial(() -> 0); // convert me
  private final int index;

  X() {
    count.set(count.get() + 1);
    index = count.get();
  }
}