// "Convert to 'ThreadLocal'" "true"
class X {

  private int x = 1;
    private final ThreadLocal<Integer> y = ThreadLocal.withInitial(() -> x);

}